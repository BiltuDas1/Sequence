package com.github.biltudas1.sequence.util

import android.content.Context
import android.widget.Toast
import timber.log.Timber

object ToastUtils {
    fun show(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        Timber.d("Toast: $message")
        Toast.makeText(context, message, duration).show()
    }
}
