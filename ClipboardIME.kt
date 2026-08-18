package com.offlineclipboardtextmanager.app

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.graphics.Color as AndroidColor

class ClipboardIME : InputMethodService() {
    private val clips = listOf(
        Clip(1, "Zoom: meeting at 3:00 — passcode 8842-116", "Work", listOf("meeting"), "2m", true),
        Clip(2, "mango-kestrel-42-violet", "Personal", listOf("wifi"), "11m", true, true),
        Clip(3, "console.log(JSON.stringify(payload, null, 2))", "Snippets", listOf("snippet"), "26m"),
        Clip(4, "214 Alder Street, Apt 6, Portland OR 97210", "Personal", listOf("address"), "40m", private = true),
        Clip(5, "Order #A-91762 — confirmation sent to inbox", "Work", listOf("order"), "1h"),
        Clip(6, "git commit -m \"fix: clamp swipe threshold\" && git push", "Snippets", listOf("snippet"), "2h"),
    )

    override fun onCreateInputView(): View {
        return createKeyboardView()
    }

    private fun createKeyboardView(): View {
        val rootLayout = LinearLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(300)
            )
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AndroidColor.WHITE)
        }

        val headerText = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            text = "Keyboard"
            textSize = 14f
            setTextColor(AndroidColor.BLACK)
            setPadding(dpToPx(14), dpToPx(14), dpToPx(14), dpToPx(8))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        rootLayout.addView(headerText)

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val clipsContainer = LinearLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
        }

        clips.forEach { clip ->
            val clipView = createClipItemView(clip)
            clipsContainer.addView(clipView)
        }

        scrollView.addView(clipsContainer)
        rootLayout.addView(scrollView)

        return rootLayout
    }

    private fun createClipItemView(clip: Clip): View {
        val itemLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
            isClickable = true
            isFocusable = true
            setOnClickListener {
                currentInputConnection?.commitText(clip.text, clip.text.length)
            }
            setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12))
        }

        val clipText = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            text = clip.text.take(50) + if (clip.text.length > 50) "..." else ""
            textSize = 12f
            setTextColor(AndroidColor.parseColor("#1A1A1A"))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        itemLayout.addView(clipText)

        val folderText = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            text = clip.folder
            textSize = 10f
            setTextColor(AndroidColor.parseColor("#9A9A9A"))
            setPadding(0, dpToPx(4), 0, 0)
        }
        itemLayout.addView(folderText)

        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                1
            )
            setBackgroundColor(AndroidColor.parseColor("#EEEEEE"))
        }
        itemLayout.addView(divider)

        return itemLayout
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
