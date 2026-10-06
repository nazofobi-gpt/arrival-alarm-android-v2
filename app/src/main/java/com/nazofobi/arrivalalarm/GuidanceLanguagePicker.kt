package com.nazofobi.arrivalalarm

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource

@Composable
fun GuidanceLanguagePicker(
    language: GuidanceLanguage,
    onSelect: (GuidanceLanguage) -> Unit,
) {
    var visible by rememberSaveable { mutableStateOf(false) }

    Text(
        text = stringResource(R.string.guidance_language_status, language.displayName),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("guidance-language-status"),
    )
    ArrivalSelectionRow(
        title = stringResource(R.string.guidance_language_title),
        value = language.displayName,
        icon = Icons.Rounded.Language,
        onClick = { visible = true },
        testTag = "guidance-language-selector",
    )
    ArrivalChoiceSheet(
        visible = visible,
        title = stringResource(R.string.guidance_language_title),
        options = GuidanceLanguage.values().toList(),
        selected = language,
        optionLabel = { it.displayName },
        optionTag = { "guidance-language-${it.localeTag}" },
        onSelect = {
            onSelect(it)
            visible = false
        },
        onDismiss = { visible = false },
    )
}
