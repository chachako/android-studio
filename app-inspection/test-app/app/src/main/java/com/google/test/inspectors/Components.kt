package com.google.test.inspectors

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun SimpleTextButton(text: String, onClick: () -> Unit) {
  Button(onClick = onClick) { Text(text) }
}
