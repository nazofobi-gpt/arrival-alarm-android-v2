package com.nazofobi.arrivalalarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun ScreenAssistPanel(
    state: ScreenAssistUiState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    guidanceText: String? = null,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("AI Ekran Asistanı", style = MaterialTheme.typography.titleMedium)
            Text(
                "Ekran paylaşımı yalnız sizin başlattığınız oturum boyunca çalışır. " +
                    "Ham ekran kareleri varsayılan olarak kaydedilmez.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("screen-assist-privacy"),
            )
            Text(
                state.message,
                modifier = Modifier.testTag("screen-assist-status"),
            )
            guidanceText?.takeIf { it.isNotBlank() }?.let { guidance ->
                Text(
                    guidance,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("screen-assist-guidance"),
                )
            }

            if (state.canStart) {
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth().testTag("screen-assist-start"),
                ) { Text("Ekran asistanını başlat") }
            }
            if (state.canPause) {
                Button(
                    onClick = onPause,
                    modifier = Modifier.fillMaxWidth().testTag("screen-assist-pause"),
                ) { Text("Duraklat") }
            }
            if (state.canResume) {
                Button(
                    onClick = onResume,
                    modifier = Modifier.fillMaxWidth().testTag("screen-assist-resume"),
                ) { Text("Devam et") }
            }
            if (state.canStop) {
                Button(
                    onClick = onStop,
                    modifier = Modifier.fillMaxWidth().testTag("screen-assist-stop"),
                ) { Text("Ekran paylaşımını durdur") }
            }
        }
    }
}
