package cz.craftplan.app

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal class InkLanguageSettings(
    private val preferences: SharedPreferences,
    default: String?,
) {
    var language by mutableStateOf(preferences.getString("language", null)?.takeIf { it in InkModels.languages } ?: default)
        private set
    var approved by mutableStateOf(preferences.getStringSet("approved_models", emptySet()).orEmpty().toSet())
        private set
    var downloadedLanguage by mutableStateOf<String?>(null)
    var chooserOpen by mutableStateOf(false)
    var downloadConfirmation by mutableStateOf<String?>(null)
    var downloading by mutableStateOf(false)
    var failed by mutableStateOf(false)
    var retry by mutableStateOf(0)
        private set
    val readyLanguage: String? get() = downloadedLanguage?.takeIf { it == language }

    fun select(tag: String) {
        if (tag !in InkModels.languages || downloading) return
        if (tag !in approved) {
            downloadConfirmation = tag
            return
        }
        choose(tag)
    }

    private fun choose(tag: String) {
        failed = false
        if (language != tag) downloadedLanguage = null
        language = tag
        preferences.edit().putString("language", tag).apply()
    }

    fun approve(tag: String) {
        choose(tag)
        approved = approved + tag
        preferences.edit().putStringSet("approved_models", approved).apply()
    }

    fun refresh() { retry++ }
}

@Composable
internal fun rememberInkLanguageSettings(): InkLanguageSettings {
    val context = LocalContext.current.applicationContext
    val locale = Locale.forLanguageTag(stringResource(R.string.app_language_tag))
    val settings = remember(context, locale) {
        InkLanguageSettings(
            context.getSharedPreferences("ink_language", 0),
            defaultInkLanguage(locale, InkModels.languages),
        )
    }
    LaunchedEffect(settings, settings.language, settings.approved, settings.retry, settings.downloading) {
        val tag = settings.language ?: return@LaunchedEffect
        if (settings.downloading || tag !in settings.approved) return@LaunchedEffect
        settings.downloadedLanguage = try {
            tag.takeIf { InkModels.isDownloaded(context, tag) }
        } catch (canceled: CancellationException) {
            throw canceled
        } catch (_: Exception) {
            null
        } catch (_: LinkageError) {
            null
        }
    }
    return settings
}

@Composable
internal fun InkLanguageDialogs(settings: InkLanguageSettings) {
    val locale = LocalConfiguration.current.locales[0]
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    if (settings.chooserOpen) {
        var query by rememberSaveable { mutableStateOf("") }
        val models = remember(locale) {
            InkModels.languages.map { it to Locale.forLanguageTag(it).getDisplayName(locale) }
                .sortedBy { it.second.lowercase(locale) }
        }
        val visible = remember(models, query) {
            models.filter { (tag, name) -> tag.contains(query, ignoreCase = true) || name.contains(query, ignoreCase = true) }
        }
        AlertDialog(
            onDismissRequest = { settings.chooserOpen = false },
            title = { Text(stringResource(R.string.ink_language)) },
            text = {
                Column {
                    settings.language?.let { Text(stringResource(R.string.ink_selected_language, it)) }
                    if (settings.downloading) Text(stringResource(R.string.ink_downloading))
                    else if (settings.failed) Text(stringResource(R.string.ink_download_failed))
                    else if (settings.readyLanguage != null) Text(stringResource(R.string.ink_ready))
                    else Text(stringResource(R.string.ink_model_needed))
                    if (!settings.downloading && settings.readyLanguage == null) {
                        settings.language?.let { tag ->
                            TextButton(onClick = {
                                settings.chooserOpen = false
                                settings.downloadConfirmation = tag
                            }) { Text(stringResource(R.string.ink_download)) }
                        }
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it.take(64) },
                        label = { Text(stringResource(R.string.ink_find_language)) },
                        singleLine = true,
                    )
                    LazyColumn(Modifier.heightIn(max = 220.dp)) {
                        items(visible, key = { it.first }) { (tag, name) ->
                            DropdownMenuItem(
                                text = { Text("$name ($tag)") },
                                enabled = !settings.downloading,
                                onClick = {
                                    settings.select(tag)
                                    if (settings.downloadConfirmation != null) settings.chooserOpen = false
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { settings.chooserOpen = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }
    settings.downloadConfirmation?.let { tag ->
        val display = Locale.forLanguageTag(tag).getDisplayName(locale)
        AlertDialog(
            onDismissRequest = { settings.downloadConfirmation = null },
            title = { Text(stringResource(R.string.ink_download_title, display)) },
            text = { Text(stringResource(R.string.ink_download_disclosure)) },
            dismissButton = {
                TextButton(onClick = { settings.downloadConfirmation = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    settings.downloadConfirmation = null
                    settings.downloading = true
                    settings.failed = false
                    settings.approve(tag)
                    settings.chooserOpen = true
                    scope.launch {
                        try {
                            if (!InkModels.isDownloaded(context, tag)) InkModels.download(context, tag)
                        } catch (canceled: CancellationException) {
                            throw canceled
                        } catch (_: Exception) {
                            settings.failed = true
                        } catch (_: LinkageError) {
                            settings.failed = true
                        } finally {
                            settings.downloading = false
                            if (!settings.failed) settings.chooserOpen = false
                            settings.refresh()
                        }
                    }
                }) { Text(stringResource(R.string.ink_download)) }
            },
        )
    }
}
