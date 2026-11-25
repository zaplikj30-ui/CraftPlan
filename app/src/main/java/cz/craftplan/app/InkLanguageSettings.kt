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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal data class InkDownloadRequest(val tag: String, val manualSelection: Boolean = false)

internal class InkLanguageSettings(
    private val preferences: SharedPreferences,
    private val default: String?,
) {
    var manualLanguage by mutableStateOf(preferences.getString("language", null)?.takeIf { it in InkModels.languages })
        private set
    val language: String? get() = manualLanguage ?: default
    var approved by mutableStateOf(preferences.getStringSet("approved_models", emptySet()).orEmpty().toSet())
        private set
    var downloadedLanguage by mutableStateOf<String?>(null)
        private set
    private var checkedLanguage: String? = null
    private var requestedLanguage: String? = null
    private val offeredLanguages = mutableSetOf<String>()
    var chooserOpen by mutableStateOf(false)
    var downloadConfirmation by mutableStateOf<InkDownloadRequest?>(null)
    var downloading by mutableStateOf(false)
    var failed by mutableStateOf(false)
    var retry by mutableStateOf(0)
        private set
    val readyLanguage: String? get() = downloadedLanguage?.takeIf { it == language }

    /** Called only after a valid, completed writing gesture, never on hover or tap. */
    fun requestModelForWriting() {
        val tag = language ?: return
        if (readyLanguage != null || downloading || downloadConfirmation != null || chooserOpen) return
        if (tag in offeredLanguages) return
        if (tag in approved && checkedLanguage != tag) {
            requestedLanguage = tag // Wait for the existing-model check, including offline.
            return
        }
        offeredLanguages += tag
        downloadConfirmation = InkDownloadRequest(tag)
    }

    fun modelChecked(tag: String, downloaded: Boolean) {
        if (tag != language || downloading) return
        downloadedLanguage = tag.takeIf { downloaded }
        checkedLanguage = tag
        if (requestedLanguage == tag) {
            requestedLanguage = null
            requestModelForWriting()
        }
    }

    fun select(tag: String) {
        if (tag !in InkModels.languages || downloading) return
        if (tag !in approved) {
            downloadConfirmation = InkDownloadRequest(tag, manualSelection = true)
            return
        }
        choose(tag)
    }

    fun selectAutomatic() {
        if (!downloading) choose(null)
    }

    private fun choose(tag: String?) {
        failed = false
        if (language != (tag ?: default)) {
            downloadedLanguage = null
            checkedLanguage = null
            requestedLanguage = null
        }
        manualLanguage = tag
        preferences.edit().apply {
            if (tag == null) remove("language") else putString("language", tag)
        }.apply()
    }

    fun approve(request: InkDownloadRequest) {
        if (request.manualSelection) choose(request.tag)
        approved = approved + request.tag
        preferences.edit().putStringSet("approved_models", approved).apply()
    }

    fun refresh() {
        checkedLanguage = null
        requestedLanguage = null
        retry++
    }
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
        val downloaded = try {
            InkModels.isDownloaded(context, tag)
        } catch (canceled: CancellationException) {
            throw canceled
        } catch (_: Exception) {
            false
        } catch (_: LinkageError) {
            false
        }
        settings.modelChecked(tag, downloaded)
    }
    return settings
}

@Composable
internal fun InkLanguageDialogs(settings: InkLanguageSettings) {
    val locale = Locale.forLanguageTag(stringResource(R.string.app_language_tag))
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    if (settings.chooserOpen) {
        var query by rememberSaveable { mutableStateOf("") }
        val models = remember(locale) {
            val named = InkModels.languages.map { it to inkLanguageName(it, locale) }
            val duplicateNames = named.groupingBy { it.second }.eachCount()
            named.map { (tag, name) ->
                tag to if ((duplicateNames[name] ?: 0) > 1) "$name ($tag)" else name
            }.sortedWith(compareBy(java.text.Collator.getInstance(locale)) { it.second })
        }
        val visible = remember(models, query) {
            models.filter { (tag, name) -> tag.contains(query, ignoreCase = true) || name.contains(query, ignoreCase = true) }
        }
        var downloaded by remember(settings) { mutableStateOf(emptySet<String>()) }
        LaunchedEffect(settings, settings.approved, settings.retry, settings.downloading) {
            if (!settings.downloading) {
                val ready = mutableSetOf<String>()
                for (tag in settings.approved.intersect(InkModels.languages)) {
                    val present = try {
                        InkModels.isDownloaded(context, tag)
                    } catch (canceled: CancellationException) {
                        throw canceled
                    } catch (_: Exception) {
                        false
                    } catch (_: LinkageError) {
                        false
                    }
                    if (present) ready += tag
                }
                downloaded = ready
            }
        }
        AlertDialog(
            onDismissRequest = { settings.chooserOpen = false },
            title = { Text(stringResource(R.string.ink_language)) },
            text = {
                Column {
                    settings.language?.let { Text(stringResource(R.string.ink_selected_language, inkLanguageName(it, locale))) }
                    Text(stringResource(if (settings.manualLanguage == null) R.string.ink_language_automatic else R.string.ink_language_manual))
                    if (settings.downloading) Text(stringResource(R.string.ink_downloading))
                    else if (settings.failed) Text(stringResource(R.string.ink_download_failed))
                    else if (settings.readyLanguage != null) Text(stringResource(R.string.ink_ready))
                    else Text(stringResource(R.string.ink_model_needed))
                    if (!settings.downloading && settings.readyLanguage == null) {
                        settings.language?.let { tag ->
                            TextButton(onClick = {
                                settings.chooserOpen = false
                                settings.downloadConfirmation = InkDownloadRequest(tag)
                            }) { Text(stringResource(R.string.ink_download)) }
                        }
                    }
                    TextButton(enabled = !settings.downloading && settings.manualLanguage != null,
                        onClick = settings::selectAutomatic) {
                        Text(stringResource(R.string.ink_language_automatic))
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
                                text = {
                                    Column {
                                        Text(name)
                                        if (tag == settings.readyLanguage || tag in downloaded) {
                                            Text(stringResource(R.string.ink_model_downloaded))
                                        }
                                    }
                                },
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
    settings.downloadConfirmation?.let { request ->
        val tag = request.tag
        val display = inkLanguageName(tag, locale)
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
                    settings.approve(request)
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
