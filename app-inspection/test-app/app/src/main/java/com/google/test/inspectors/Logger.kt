package com.google.test.inspectors

import android.util.Log

private const val TAG = "InspectorApp"

internal object Logger {

  fun info(text: String) {
    Log.i(TAG, text)
  }

  fun error(text: String, e: Throwable? = null) {
    Log.e(TAG, text, e)
  }
}
