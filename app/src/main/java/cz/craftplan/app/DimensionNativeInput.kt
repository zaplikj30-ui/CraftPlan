package cz.craftplan.app

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.Selection
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** Keep an Android input connection available independently of soft keyboard visibility. */
// Created by AndroidView with the actual field role, never inflated from XML.
@SuppressLint("ViewConstructor")
internal class DimensionNativeInput(context: Context, private val field: DimensionField) : EditText(context) {
    var onTap: () -> Unit = {}
    var onInput: (TextFieldValue) -> Unit = {}
    var onPenContact: (PenContact) -> Unit = {}
    private var applyingDraft = false
    private var penDown = false
    // The first tap can be consumed by View taking focus without dispatching performClick.
    private val taps = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (e.flags and MotionEvent.FLAG_CANCELED == 0) onTap()
            return true
        }
    })

    init {
        background = null
        setPadding(0, 0, 0, 0)
        includeFontPadding = false
        setSingleLine(true)
        inputType = InputType.TYPE_CLASS_TEXT or if (field == DimensionField.NOTE) {
            InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
        } else {
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        showSoftInputOnFocus = false
        importantForAutofill = IMPORTANT_FOR_AUTOFILL_NO
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            isAutoHandwritingEnabled = true
            setHandwritingBoundsOffsets(0f, 0f, 0f, 0f)
        } else {
            isAutoHandwritingEnabled = false
        }
        if (field == DimensionField.NOTE) {
            filters = arrayOf(InputFilter { source, start, end, dest, dstart, dend ->
                if (permitsNoteEdit(dest.length, dend - dstart, end - start)) null
                else dest.subSequence(dstart, dend)
            })
        }
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = publishInput()
        })
    }

    fun applyDraft(value: TextFieldValue) {
        val current = text ?: return
        // Replacing the Editable on every recomposition would discard IME composition and spans.
        if (current.toString() == value.text && selectionStart == value.selection.start &&
            selectionEnd == value.selection.end
        ) return
        applyingDraft = true
        try {
            if (current.toString() != value.text) setText(value.text)
            val updated = text ?: return
            Selection.setSelection(
                updated, value.selection.start.coerceIn(0, updated.length),
                value.selection.end.coerceIn(0, updated.length),
            )
        } finally {
            applyingDraft = false
        }
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (selStart >= 0 && selEnd >= 0) publishInput()
    }

    // Standard TextView handling stays in super; the extra activation has an ACTION_CLICK path below.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    penDown = event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS ||
                        event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER
                    if (penDown) onPenContact(PenContact.START)
                }
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                    if (penDown) {
                        val canceled = event.actionMasked == MotionEvent.ACTION_CANCEL ||
                            event.flags and MotionEvent.FLAG_CANCELED != 0
                        onPenContact(if (canceled) PenContact.CANCELED else PenContact.UP)
                    }
                    penDown = false
                }
            }
        }
        val handled = super.onTouchEvent(event)
        taps.onTouchEvent(event)
        return handled
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        val handled = super.performAccessibilityAction(action, arguments)
        if (action != AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK.id) return handled
        onTap()
        return true
    }

    private fun publishInput() {
        if (applyingDraft) return
        val content = text?.toString() ?: return
        val start = selectionStart.takeIf { it >= 0 } ?: content.length
        val end = selectionEnd.takeIf { it >= 0 } ?: start
        onInput(TextFieldValue(content, TextRange(start.coerceAtMost(content.length), end.coerceAtMost(content.length))))
    }
}

internal enum class PenContact { START, UP, CANCELED }

internal fun permitsNoteEdit(oldLength: Int, removed: Int, inserted: Int): Boolean {
    val nextLength = oldLength - removed + inserted
    return nextLength <= MAX_DIMENSION_NOTE_LENGTH || nextLength < oldLength
}

internal fun requiresExplicitSaveForNativeInput(api: Int, previous: String, next: String): Boolean =
    api >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && previous != next
