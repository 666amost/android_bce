package com.bce.courier.data.model

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.Locale
import com.bce.courier.util.PickupAwbNumber

fun normalizePaymentMethod(value: String): String = when (value.trim().lowercase(Locale.ROOT)) {
    "cash", "tunai" -> "cash"
    "transfer", "trf" -> "transfer"
    "cod" -> "cod"
    else -> ""
}

fun paymentMethodLabel(value: String): String = when (normalizePaymentMethod(value)) {
    "cash" -> "Cash"
    "transfer" -> "Transfer (TRF)"
    "cod" -> "COD"
    else -> value.ifBlank { "Belum dipilih" }
}

@Serializable
data class PickupFormState(
    val awbNo: String = "",
    val awbDate: String = LocalDate.now().toString(),
    val kirimVia: String = "udara",
    val kotaTujuan: String = "",
    val wilayah: String = "",
    val metodePembayaran: String = "cash",
    val agentCustomer: String = "",
    val namaPengirim: String = "",
    val nomorPengirim: String = "",
    val namaPenerima: String = "",
    val nomorPenerima: String = "",
    val alamatPenerima: String = "",
    val coli: String = "1",
    val beratKg: String = "1",
    val hargaPerKg: String = "0",
    val biayaAdmin: String = "0",
    val biayaPackaging: String = "0",
    val biayaTransit: String = "0",
    val isiBarang: String = "",
    val paymentManuallySelected: Boolean = false,
    val dateManuallySelected: Boolean = false,
    val priceManuallyEdited: Boolean = false,
    val submissionAttempted: Boolean = false,
) {
    fun withCurrentDate(today: LocalDate = LocalDate.now()): PickupFormState =
        if (dateManuallySelected || submissionAttempted) this else copy(awbDate = today.toString())

    fun withNewAwb(today: LocalDate = LocalDate.now()): PickupFormState =
        copy(submissionAttempted = false).withCurrentDate(today).copy(awbNo = PickupAwbNumber.generate())

    fun withCustomer(customer: CustomerData): PickupFormState = copy(
        namaPengirim = customer.namaPengirim, nomorPengirim = customer.nomorPengirim,
        namaPenerima = customer.namaPenerima, nomorPenerima = customer.nomorPenerima,
        alamatPenerima = customer.alamatPenerima, kotaTujuan = customer.kotaTujuan,
        wilayah = customer.wilayah, kirimVia = customer.kirimVia.ifBlank { "udara" },
        metodePembayaran = if (paymentManuallySelected) metodePembayaran else
            normalizePaymentMethod(customer.metodePembayaran).ifBlank { metodePembayaran },
        agentCustomer = customer.agentCustomer, isiBarang = customer.isiBarang,
    )
}
