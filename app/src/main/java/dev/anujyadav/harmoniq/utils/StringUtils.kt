package dev.anujyadav.harmoniq.utils

fun String.withCase(sensitive: Boolean) = if (!sensitive) lowercase() else this
