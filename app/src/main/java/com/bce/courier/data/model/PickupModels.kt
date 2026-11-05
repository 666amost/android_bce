package com.bce.courier.data.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class ManifestPayload(
    val awbNo: String,
    val awbDate: String,
    val kirimVia: String,
    val kotaTujuan: String,
    val wilayah: String,
    val metodePembayaran: String,
    val agentCustomer: String,
    val namaPengirim: String,
    val nomorPengirim: String,
    val namaPenerima: String,
    val nomorPenerima: String,
    val alamatPenerima: String,
    val coli: Int,
    val beratKg: Double,
    val hargaPerKg: Int,
    val subTotal: Int,
    val biayaAdmin: Int,
    val biayaPackaging: Int,
    val biayaTransit: Int,
    val total: Int,
    val isiBarang: String,
    val courierId: String = "",
    val isRetry: Boolean = false,
) {
    fun matchesStored(stored: JsonObject): Boolean = toJsonObject().all { (key, expected) ->
        val actual = stored[key]?.jsonPrimitive?.contentOrNull ?: return@all false
        val value = expected.jsonPrimitive.content
        when (key) {
            "metode_pembayaran" -> normalizePaymentMethod(actual) == normalizePaymentMethod(value)
            "awb_date" -> actual.substringBefore('T') == value
            "coli", "berat_kg", "harga_per_kg", "sub_total", "biaya_admin", "biaya_packaging", "biaya_transit", "total" ->
                actual.toDoubleOrNull() == value.toDoubleOrNull()
            else -> actual.trim() == value.trim()
        }
    }

    fun toJsonObject(): JsonObject = buildJsonObject {
        put("awb_no", awbNo)
        put("awb_date", awbDate)
        put("kirim_via", kirimVia)
        put("kota_tujuan", kotaTujuan)
        put("wilayah", wilayah)
        put("metode_pembayaran", metodePembayaran)
        put("agent_customer", agentCustomer)
        put("nama_pengirim", namaPengirim)
        put("nomor_pengirim", nomorPengirim)
        put("nama_penerima", namaPenerima)
        put("nomor_penerima", nomorPenerima)
        put("alamat_penerima", alamatPenerima)
        put("coli", coli)
        put("berat_kg", beratKg)
        put("harga_per_kg", hargaPerKg)
        put("sub_total", subTotal)
        put("biaya_admin", biayaAdmin)
        put("biaya_packaging", biayaPackaging)
        put("biaya_transit", biayaTransit)
        put("total", total)
        put("isi_barang", isiBarang)
        if (courierId.isNotBlank()) {
            put("courier_id", courierId)
        }
    }
}

data class CustomerData(
    val id: String,
    val namaPengirim: String,
    val nomorPengirim: String,
    val namaPenerima: String,
    val nomorPenerima: String,
    val alamatPenerima: String,
    val kotaTujuan: String,
    val wilayah: String,
    val kirimVia: String,
    val metodePembayaran: String,
    val agentCustomer: String,
    val isiBarang: String
) {
    companion object {
        fun fromJsonObject(json: JsonObject): CustomerData {
            return CustomerData(
                id = json["id"]?.jsonPrimitive?.content ?: "",
                namaPengirim = json["nama_pengirim"]?.jsonPrimitive?.content ?: "",
                nomorPengirim = json["nomor_pengirim"]?.jsonPrimitive?.content ?: "",
                namaPenerima = json["nama_penerima"]?.jsonPrimitive?.content ?: "",
                nomorPenerima = json["nomor_penerima"]?.jsonPrimitive?.content ?: "",
                alamatPenerima = json["alamat_penerima"]?.jsonPrimitive?.content ?: "",
                kotaTujuan = json["kota_tujuan"]?.jsonPrimitive?.content ?: "",
                wilayah = json["wilayah"]?.jsonPrimitive?.content ?: "",
                kirimVia = json["kirim_via"]?.jsonPrimitive?.content ?: "",
                metodePembayaran = json["metode_pembayaran"]?.jsonPrimitive?.content ?: "",
                agentCustomer = json["agent_customer"]?.jsonPrimitive?.content ?: "",
                isiBarang = json["isi_barang"]?.jsonPrimitive?.content ?: ""
            )
        }
    }
}

data class ManifestItem(
    val awbNo: String,
    val awbDate: String,
    val namaPengirim: String,
    val namaPenerima: String,
    val alamatPenerima: String,
    val wilayah: String,
    val total: Int,
    val nomorPengirim: String = "",
    val nomorPenerima: String = "",
    val kirimVia: String = "",
    val kotaTujuan: String = "",
    val layanan: String = "",
    val metodePembayaran: String = "",
    val coli: Int = 0,
    val beratKg: Double = 0.0,
    val hargaPerKg: Int = 0,
    val subTotal: Int = 0,
    val biayaAdmin: Int = 0,
    val biayaPackaging: Int = 0,
    val biayaTransit: Int = 0,
    val isiBarang: String = "",
    val agentCustomer: String = ""
) {
    companion object {
        fun fromJsonObject(json: JsonObject): ManifestItem {
            val awbNo = json["awb_no"]?.jsonPrimitive?.content ?: ""
            val awbDate = json["awb_date"]?.jsonPrimitive?.content ?: ""
            val namaPengirim = json["nama_pengirim"]?.jsonPrimitive?.content ?: ""
            val namaPenerima = json["nama_penerima"]?.jsonPrimitive?.content ?: ""
            val alamatPenerima = json["alamat_penerima"]?.jsonPrimitive?.content ?: ""
            val wilayah = json["wilayah"]?.jsonPrimitive?.content ?: ""
            val totalStr = json["total"]?.jsonPrimitive?.content
            val total = totalStr?.toIntOrNull() ?: 0

            val nomorPengirim = json["nomor_pengirim"]?.jsonPrimitive?.content ?: ""
            val nomorPenerima = json["nomor_penerima"]?.jsonPrimitive?.content ?: ""
            val kirimVia = json["kirim_via"]?.jsonPrimitive?.content ?: ""
            val kotaTujuan = json["kota_tujuan"]?.jsonPrimitive?.content ?: ""
            val metodePembayaran = json["metode_pembayaran"]?.jsonPrimitive?.content ?: ""
            val isiBarang = json["isi_barang"]?.jsonPrimitive?.content ?: ""
            val agentCustomer = json["agent_customer"]?.jsonPrimitive?.content ?: ""

            val coli = json["coli"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val beratKg = json["berat_kg"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
            val hargaPerKg = json["harga_per_kg"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val subTotal = json["sub_total"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val biayaAdmin = json["biaya_admin"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val biayaPackaging = json["biaya_packaging"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val biayaTransit = json["biaya_transit"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0

            return ManifestItem(
                awbNo = awbNo,
                awbDate = awbDate,
                namaPengirim = namaPengirim,
                namaPenerima = namaPenerima,
                alamatPenerima = alamatPenerima,
                wilayah = wilayah,
                nomorPengirim = nomorPengirim,
                nomorPenerima = nomorPenerima,
                kirimVia = kirimVia,
                kotaTujuan = kotaTujuan,
                layanan = kirimVia,
                metodePembayaran = metodePembayaran,
                coli = coli,
                beratKg = beratKg,
                hargaPerKg = hargaPerKg,
                subTotal = subTotal,
                biayaAdmin = biayaAdmin,
                biayaPackaging = biayaPackaging,
                biayaTransit = biayaTransit,
                isiBarang = isiBarang,
                total = total,
                agentCustomer = agentCustomer
            )
        }
    }
}
