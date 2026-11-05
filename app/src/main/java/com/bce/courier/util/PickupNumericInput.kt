package com.bce.courier.util

/** Null rejects invalid input; an empty string remains editable until focus leaves. */
fun normalizePickupNumber(value: String, allowDecimal: Boolean): String? {
    val pattern = if (allowDecimal) Regex("[0-9]*([.,][0-9]*)?") else Regex("[0-9]*")
    if (!pattern.matches(value)) return null
    if (value.isEmpty()) return ""
    val normalized = value.replace(',', '.')
    val whole = normalized.substringBefore('.').trimStart('0').ifEmpty { "0" }
    return if ('.' in normalized) "$whole.${normalized.substringAfter('.')}" else whole
}
