package com.bce.courier.debug

import android.media.MediaPlayer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bce.courier.R
import com.bce.courier.data.model.DeliveryStatus
import com.bce.courier.data.model.DeliveryUpdateOutcome
import com.bce.courier.data.model.ShipmentItem
import com.bce.courier.ui.component.DeliveryCompletion
import com.bce.courier.ui.screen.UpdateShipmentScreen
import com.bce.courier.ui.theme.BCECourierTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Debug-only simulation. No repository, login, uploads, or delivery mutations. */
class DeliveryFeedbackPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BCECourierTheme {
                val scope = rememberCoroutineScope()
                var saving by remember { mutableStateOf(false) }
                var completion by remember { mutableStateOf<DeliveryCompletion?>(null) }
                var scenario by remember { mutableIntStateOf(0) }
                var hasPlayed by remember { mutableStateOf(false) }
                var player by remember { mutableStateOf<MediaPlayer?>(null) }
                DisposableEffect(Unit) { onDispose { player?.release() } }
                val simulate: (String) -> Unit = { status ->
                    if (!saving && completion == null) {
                        saving = true
                        scope.launch {
                            delay(450)
                            completion = DeliveryCompletion(
                                status,
                                if (scenario == 1) DeliveryUpdateOutcome.UPDATED else DeliveryUpdateOutcome.QUEUED,
                            )
                            saving = false
                            hasPlayed = true
                            if (DeliveryStatus.isDelivered(status)) {
                                player?.release()
                                player = MediaPlayer.create(this@DeliveryFeedbackPreviewActivity, R.raw.scan_delivered)
                                    ?.apply { start() }
                            }
                        }
                    }
                }
                Column(Modifier.fillMaxSize().navigationBarsPadding()) {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                            Text("PRATINJAU · Data contoh", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Antre sync", "Cloud synced", "Pending").forEachIndexed { index, label ->
                                    FilterChip(
                                        selected = scenario == index,
                                        onClick = { scenario = index },
                                        enabled = !saving && completion == null,
                                        label = { Text(label, fontSize = 11.sp) },
                                    )
                                }
                            }
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        UpdateShipmentScreen(
                            shipment = ShipmentItem(
                                awb = intent.getStringExtra("awb") ?: "BCE172694JKT",
                                recipient_name = "Amos · Contoh penerima",
                                address = "Jakarta",
                                current_status = DeliveryStatus.OUT_FOR_DELIVERY,
                            ),
                            onBack = { finish() },
                            onUpdate = { status, _, _, _, _, _, _ -> simulate(status) },
                            isUpdating = saving,
                            completion = completion,
                            onCompletionFinished = { completion = null },
                        )
                        if (!saving && completion == null) {
                            Button(
                                onClick = {
                                    simulate(if (scenario == 2) DeliveryStatus.PENDING else DeliveryStatus.DELIVERED)
                                },
                                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 12.dp).height(52.dp),
                            ) {
                                Text(if (hasPlayed) "Ulangi simulasi" else "Simulasikan update berhasil")
                            }
                        }
                    }
                }
                LaunchedEffect(Unit) {
                    if (intent.getBooleanExtra("autoplay", false)) {
                        delay(1500)
                        simulate(DeliveryStatus.DELIVERED)
                    }
                }
            }
        }
    }
}
