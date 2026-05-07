package com.washdog.app.util

import java.text.Normalizer

private val NON_ASCII = Regex("[^\\p{ASCII}]")

fun String.normalized(): String = Normalizer.normalize(this, Normalizer.Form.NFD)
    .replace(NON_ASCII, "")
    .lowercase()

fun String.fuzzyContains(query: String): Boolean =
    this.normalized().contains(query.trim().normalized())
