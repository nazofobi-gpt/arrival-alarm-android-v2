package com.nazofobi.arrivalalarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                ArrivalAlarmApp()
            }
        }
    }
}

@Composable
fun ArrivalAlarmApp() {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Nereye gidiyoruz?",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "Temiz V2 başlangıcı · Android 16 / API ${BuildContract.TARGET_SDK}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "İlk kapı: GitHub Actions üzerinde tekrarlanabilir build ve API 36 emulator testi.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ArrivalAlarmPreview() {
    MaterialTheme {
        ArrivalAlarmApp()
    }
}
