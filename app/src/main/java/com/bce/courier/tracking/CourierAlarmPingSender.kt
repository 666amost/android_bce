package com.bce.courier.tracking

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.gojek.mqtt.pingsender.IPingSenderEvents
import com.gojek.mqtt.pingsender.MqttPingSender
import com.gojek.mqtt.pingsender.NoOpPingSenderEvents
import org.eclipse.paho.client.mqttv3.ILogger
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.internal.ClientComms
import java.util.UUID

/** The library's alarm sender shares one PendingIntent; each broker needs its own. */
internal class CourierAlarmPingSender(
    private val context: Context,
    brokerId: String,
) : MqttPingSender {
    private val action = "${context.packageName}.MQTT_PING.$brokerId.${UUID.randomUUID()}"
    private val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private lateinit var comms: ClientComms
    private var events: IPingSenderEvents = NoOpPingSenderEvents()
    private var pendingIntent: PendingIntent? = null
    @Volatile
    private var started = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == action && started) sendPing()
        }
    }

    override fun init(comms: ClientComms, logger: ILogger) {
        this.comms = comms
    }

    @Synchronized
    override fun start() {
        if (started) return
        try {
            ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
            started = true
            pendingIntent = PendingIntent.getBroadcast(
                context, 0, Intent(action).setPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            schedule(comms.keepAlive)
        } catch (error: Exception) {
            stop()
            events.exceptionInStart(error)
        }
    }

    @Synchronized
    override fun stop() {
        if (!started) return
        started = false
        pendingIntent?.let { alarms.cancel(it); it.cancel() }
        pendingIntent = null
        runCatching { context.unregisterReceiver(receiver) }
    }

    @SuppressLint("ScheduleExactAlarm")
    @Synchronized
    override fun schedule(delayInMilliseconds: Long) {
        if (!started) return
        val alarm = pendingIntent ?: return
        val triggerAt = SystemClock.elapsedRealtime() + delayInMilliseconds
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, alarm)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, alarm)
            }
            events.mqttPingScheduled(delayInMilliseconds / 1_000L, comms.keepAlive / 1_000L)
        } catch (error: Exception) {
            events.exceptionInStart(error)
        }
    }

    override fun setPingEventHandler(pingSenderEvents: IPingSenderEvents) {
        events = pingSenderEvents
    }

    private fun sendPing() {
        val serverUri = comms.client.serverURI
        val keepAlive = comms.keepAlive / 1_000L
        val startedAt = SystemClock.elapsedRealtime()
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bce:mqtt-ping")
        try {
            wakeLock.acquire(10_000L)
            events.mqttPingInitiated(serverUri, keepAlive)
            val token = comms.checkForActivity(true)
            if (token == null) {
                if (wakeLock.isHeld) wakeLock.release()
                events.pingMqttTokenNull(serverUri, keepAlive)
                return
            }
            token.actionCallback = object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    if (wakeLock.isHeld) wakeLock.release()
                    events.pingEventSuccess(serverUri, SystemClock.elapsedRealtime() - startedAt, keepAlive)
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable) {
                    if (wakeLock.isHeld) wakeLock.release()
                    events.pingEventFailure(serverUri, SystemClock.elapsedRealtime() - startedAt, exception, keepAlive)
                }
            }
        } catch (error: Exception) {
            if (wakeLock.isHeld) wakeLock.release()
            events.exceptionInStart(error)
        }
    }
}
