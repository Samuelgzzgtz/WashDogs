package com.washdog.app.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

private const val PHONE_PREFIX = "+52 "

private object PhoneMaskTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val out = AnnotatedString(PHONE_PREFIX + text.text)
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = offset + PHONE_PREFIX.length
            override fun transformedToOriginal(offset: Int) =
                (offset - PHONE_PREFIX.length).coerceAtLeast(0).coerceAtMost(text.length)
        }
        return TransformedText(out, mapping)
    }
}

@Composable
fun WDPhoneField(
    digits: String,
    onDigitsChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value                = digits,
        onValueChange        = { new ->
            val filtered = new.filter { it.isDigit() }.take(10)
            onDigitsChange(filtered)
        },
        label                = { Text("Teléfono") },
        placeholder          = { Text("10 dígitos") },
        visualTransformation = PhoneMaskTransformation,
        keyboardOptions      = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine           = true,
        modifier             = modifier
    )
}

fun digitsToPhone(digits: String): String = "+52$digits"

fun phoneToDigits(phone: String): String =
    phone.removePrefix("+52").removePrefix(" ").filter { it.isDigit() }
