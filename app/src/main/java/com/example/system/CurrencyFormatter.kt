package com.example.system

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

fun formatKsh(amount: Double, decimals: Int = 2): String {
    val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = decimals
        maximumFractionDigits = decimals
        isGroupingUsed = true
    }
    val prefix = if (amount < 0.0) "-KSh " else "KSh "
    return prefix + formatter.format(abs(amount))
}

fun formatKshSigned(amount: Double, decimals: Int = 2): String {
    val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = decimals
        maximumFractionDigits = decimals
        isGroupingUsed = true
    }
    val prefix = when {
        amount > 0.0 -> "+KSh "
        amount < 0.0 -> "-KSh "
        else -> "KSh "
    }
    return prefix + formatter.format(abs(amount))
}
