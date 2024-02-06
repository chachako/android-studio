package com.example.composeapplication

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import com.example.composeapplication.ui.theme.ComposeApplicationTheme

@Preview
@Composable
fun PreviewSmall() {
    ComposeApplicationTheme {
        Button(onClick = {}) {
            Text("Ok")
        }
    }
}

@Preview
@Composable
fun PreviewLarge() {
    ComposeApplicationTheme {
        Scaffold(
            topBar = {
                Text(text = "Title")
            },
            bottomBar = {
                Row {
                    Button(onClick = {}) {
                        Text(text = "Ok")
                    }
                    Button(onClick = {}) {
                        Text(text = "Cancel")
                    }
                }
            }
        ) { padding ->
            LazyColumn(modifier = Modifier.padding(padding)) {
                (0..10).forEach {  i ->
                    item {
                        Row {
                            Image(painter = painterResource(id = R.drawable.ic_launcher_foreground), contentDescription = "Android face")
                            Text(text = "Row $i")
                            Checkbox(checked = i % 2 == 0, onCheckedChange = {})
                        }
                    }
                }
            }
        }
    }
}
