package com.bce.courier.util

object PickupAwbNumber {
    private val numericFormat = Regex("^BCE[0-9]+JKT$")

    fun generate(nowMillis: Long = System.currentTimeMillis()): String {
        val digits = Math.floorMod(nowMillis, 1_000_000L).toString().padStart(6, '0')
        return "BCE${digits}JKT"
    }

    fun isNumeric(awb: String): Boolean = numericFormat.matches(awb)

    fun withLeadingZero(awb: String): String {
        require(isNumeric(awb)) { "Nomor resi duplikat. Tekan Generate untuk membuat nomor baru." }
        return "BCE0${awb.removePrefix("BCE")}"
    }
}
