package com.google.test.inspectors

import android.util.Log

private const val TAG = "InspectorApp"

internal object Logger {

  fun info(text: String) {
    Log.i(TAG, text)
  }
}
