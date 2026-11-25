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
import com.google.mlkit.vision.digitalink.recognition.RecognitionContext
import com.google.mlkit.vision.digitalink.recognition.WritingArea
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class PenPoint(val x: Float, val y: Float, val timeMs: Long)

internal data class InkWritingArea(val width: Float, val height: Float)
internal data class InkRecognitionHints(
    val preContext: String, val area: InkWritingArea? = null,
    val diagnosticCandidates: ((List<String>) -> Unit)? = null,
)

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

/** Resolve the same selected language as the dialog, but never initialize an unapproved model. */
internal fun approvedInkWarmupLanguage(
    locale: Locale, available: Set<String>, manual: String?, approved: Set<String>,
): String? = (manual?.takeIf { it in available } ?: defaultInkLanguage(locale, available))
    ?.takeIf { it in approved }

/** Share only running checks for the same model, never their completed results.
 * Acquire on a worker; completion removes only the exact task that finished. */
internal class InFlightModelChecks<T : Any>(private val isComplete: (T) -> Boolean) {
    private val running = ConcurrentHashMap<String, T>()

    @Synchronized
    fun acquire(tag: String, create: () -> T): Pair<T, Boolean> {
        val existing = running[tag]
        if (existing != null) {
            if (!isComplete(existing)) return existing to true
            running.remove(tag, existing)
        }
        val task = create()
        running[tag] = task
        return task to false
    }

    fun release(tag: String, task: T) {
        running.remove(tag, task)
    }
}

internal object InkModels {
    private val checks = InFlightModelChecks<Task<Boolean>> { it.isComplete }
    private val directExecutor = Executor { it.run() }
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

    /** Run only on a worker. Select the approved model without changing preferences. */
    fun prepareForEditor(context: Context, locale: Locale): String? {
        val available = languages
        val prefs = context.getSharedPreferences("ink_language", Context.MODE_PRIVATE)
        return approvedInkWarmupLanguage(locale, available, prefs.getString("language", null),
            prefs.getStringSet("approved_models", emptySet()).orEmpty())
    }

    suspend fun isDownloaded(context: Context, tag: String, diagnostics: EditorDiagnostics? = null): Boolean {
        // RemoteModelManager is thread-safe. Keep potentially expensive SDK setup off
        // the UI thread; state changes and diagnostic recorder calls stay with the caller.
        diagnostics?.keypadStage(KeypadDiagnosticStage.MODEL_TASK_START)
        val (task, elapsed, joined) = withContext(Dispatchers.IO) {
            val start = System.nanoTime()
            val (active, shared) = checks.acquire(tag) { manager(context).isModelDownloaded(model(tag)) }
            if (!shared) {
                // The SDK task can outlive a canceled caller. Keep it registered until
                // actual completion, then discard it without retaining availability.
                active.addOnCompleteListener(directExecutor) { checks.release(tag, active) }
            }
            Triple(active, System.nanoTime() - start, shared)
        }
        diagnostics?.keypadStage(if (joined) KeypadDiagnosticStage.MODEL_TASK_JOINED
            else KeypadDiagnosticStage.MODEL_TASK_CREATED, elapsed)
        return task.awaitInkTask()
    }

    /** Only called from the explicit model-download confirmation. */
    suspend fun download(context: Context, tag: String) {
        manager(context).download(model(tag), DownloadConditions.Builder().build()).awaitInkTask()
    }

    suspend fun recognize(tag: String, strokes: List<List<PenPoint>>, hints: InkRecognitionHints): String? {
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
            val context = RecognitionContext.builder().setPreContext(hints.preContext)
            hints.area?.takeIf { it.width.isFinite() && it.height.isFinite() &&
                it.width > 0f && it.height > 0f }?.let {
                context.setWritingArea(WritingArea(it.width, it.height))
            }
            val candidates = recognizer.recognize(ink.build(), context.build()).awaitInkTask().candidates
            // A diagnostic failure must not discard a valid recognition result.
            try {
                hints.diagnosticCandidates?.invoke(candidates.map { it.text })
            } catch (_: RuntimeException) { /* Diagnostics are best effort. */ }
            candidates.firstOrNull()?.text
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
