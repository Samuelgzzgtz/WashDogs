package com.washdog.app.util

import java.text.Normalizer

fun String.normalized(): String = Normalizer.normalize(this, Normalizer.Form.NFD)
    .replace(Regex("[^\\p{ASCII}]"), "")
    .lowercase()

fun String.fuzzyContains(query: String): Boolean =
    this.normalized().contains(query.normalized())
