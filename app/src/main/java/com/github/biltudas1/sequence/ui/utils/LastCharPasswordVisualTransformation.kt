package com.github.biltudas1.sequence.ui.utils

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

class LastCharPasswordVisualTransformation(
    private val maskLast: Boolean,
    private val maskChar: Char = '\u2022'
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val originalText = text.text
        if (originalText.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        val transformed = if (maskLast) {
            maskChar.toString().repeat(originalText.length)
        } else {
            maskChar.toString().repeat(originalText.length - 1) + originalText.last()
        }

        return TransformedText(AnnotatedString(transformed), OffsetMapping.Identity)
    }
}
