package com.nazofobi.arrivalalarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun ScreenAssistPanel(
    state: ScreenAssistUiState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    brokerEndpointConfigured: Boolean,
    brokerCredentialConfigured: Boolean,
    brokerCredentialDraft: String,
    onBrokerCredentialDraftChange: (String) -> Unit,
    onSaveBrokerCredential: () -> Unit,
    onClearBrokerCredential: () -> Unit,
    pendingConfirmationText: String? = null,
    onApprovePendingCommand: () -> Unit = {},
    onRejectPendingCommand: () -> Unit = {},
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
            Text(
                when {
                    !brokerEndpointConfigured ->
                        "Broker HTTPS adresi bu build için yapılandırılmamış"
                    brokerCredentialConfigured ->
                        "Broker kimliği cihazda şifreli olarak hazır"
                    else ->
                        "Broker kimliği gerekli • bu olmadan ekran paylaşımı başlatılmaz"
                },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("screen-assist-broker-status"),
            )
            OutlinedTextField(
                value = brokerCredentialDraft,
                onValueChange = onBrokerCredentialDraftChange,
                label = { Text("Özel broker erişim anahtarı") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("screen-assist-broker-token"),
            )
            if (brokerCredentialDraft.isNotBlank()) {
                Button(
                    onClick = onSaveBrokerCredential,
                    modifier = Modifier.fillMaxWidth().testTag("screen-assist-broker-save"),
                ) { Text("Cihazda şifreli kaydet") }
            }
            if (brokerCredentialConfigured) {
                Button(
                    onClick = onClearBrokerCredential,
                    modifier = Modifier.fillMaxWidth().testTag("screen-assist-broker-clear"),
                ) { Text("Broker kimliğini sil") }
            }
            pendingConfirmationText?.takeIf { it.isNotBlank() }?.let { pending ->
                Text(
                    "Onay gerekli: $pending",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("screen-assist-command-confirmation"),
                )
                Button(
                    onClick = onApprovePendingCommand,
                    modifier = Modifier.fillMaxWidth().testTag("screen-assist-command-approve"),
                ) { Text("Onayla") }
                Button(
                    onClick = onRejectPendingCommand,
                    modifier = Modifier.fillMaxWidth().testTag("screen-assist-command-reject"),
                ) { Text("Reddet") }
            }
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
                    enabled = brokerEndpointConfigured && brokerCredentialConfigured,
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
