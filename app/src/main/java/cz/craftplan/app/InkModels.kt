package cz.craftplan.app

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.MlKit
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

internal data class PenPoint(val x: Float, val y: Float, val timeMs: Long)

internal fun isTextInkLanguage(tag: String): Boolean {
    val locale = Locale.forLanguageTag(tag)
    return locale.language.isNotEmpty() && locale.language != "und" && locale.language != "zxx" &&
        locale.getExtension('x')?.split('-')?.contains("gesture") != true
}

internal fun inkLanguageName(tag: String, locale: Locale): String =
    Locale.forLanguageTag(tag).getDisplayName(locale).ifBlank { tag }
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

internal fun defaultInkLanguage(appLocale: Locale, available: Set<String>): String? {
    val localized = Locale.lookupTag(listOf(Locale.LanguageRange(appLocale.toLanguageTag())), available)
    return localized ?: available.firstOrNull { Locale.forLanguageTag(it).language == appLocale.language }
        ?: available.firstOrNull { it == "en" }
        ?: available.firstOrNull()
}

internal object InkModels {
    private var initialized = false
    private val identifiers by lazy {
        try {
            DigitalInkRecognitionModelIdentifier.allModelIdentifiers()
                .associateBy { it.languageTag }
        } catch (_: LinkageError) {
            emptyMap()
        } catch (_: RuntimeException) {
            emptyMap()
        }
    }

    // Keep the full SDK catalog for future gesture support, but expose only text models here.
    val languages: Set<String> by lazy { identifiers.keys.filter(::isTextInkLanguage).toSet() }

    @Synchronized
    private fun manager(context: Context): RemoteModelManager {
        if (!initialized) {
            MlKit.initialize(context.applicationContext)
            initialized = true
        }
        return RemoteModelManager.getInstance()
    }

    private fun model(tag: String): DigitalInkRecognitionModel =
        DigitalInkRecognitionModel.builder(identifiers.getValue(tag)).build()

    suspend fun isDownloaded(context: Context, tag: String, diagnostics: EditorDiagnostics? = null): Boolean {
        diagnostics?.keypadStage(KeypadDiagnosticStage.MODEL_TASK_START)
        val start = if (diagnostics != null) System.nanoTime() else 0L
        val task = try {
            manager(context).isModelDownloaded(model(tag))
        } finally {
            if (diagnostics != null) diagnostics.keypadStage(KeypadDiagnosticStage.MODEL_TASK_CREATED, System.nanoTime() - start)
        }
        return task.awaitInkTask()
    }

    /** Only called from the explicit model-download confirmation. */
    suspend fun download(context: Context, tag: String) {
        manager(context).download(model(tag), DownloadConditions.Builder().build()).awaitInkTask()
    }

    suspend fun recognize(tag: String, strokes: List<List<PenPoint>>): String? {
        val ink = Ink.builder()
        for (points in strokes) {
            val stroke = Ink.Stroke.builder()
            for (point in points) stroke.addPoint(Ink.Point.create(point.x, point.y, point.timeMs))
            ink.addStroke(stroke.build())
        }
        val recognizer = DigitalInkRecognition.getClient(
            DigitalInkRecognizerOptions.builder(model(tag)).build(),
        )
        return try {
            recognizer.recognize(ink.build()).awaitInkTask().candidates.firstOrNull()?.text
        } finally {
            recognizer.close()
        }
    }
}

private suspend fun <T> Task<T>.awaitInkTask(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        when {
            task.isCanceled -> continuation.cancel()
            task.isSuccessful -> continuation.resume(task.result)
            else -> continuation.resumeWithException(task.exception ?: IllegalStateException("ML Kit task failed"))
        }
    }
}
