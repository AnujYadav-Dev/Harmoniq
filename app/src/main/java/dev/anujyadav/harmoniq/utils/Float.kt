package dev.anujyadav.harmoniq.utils

fun Float.toSafeFinite() = if (!isFinite()) 0f else this
