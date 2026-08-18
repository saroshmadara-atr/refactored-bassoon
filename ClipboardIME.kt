package com.offlineclipboardtextmanager.app

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.graphics.Color as AndroidColor
import android.view.KeyEvent

class ClipboardIME : InputMethodService() {
    private val EMOJIS: List<String> = listOf(
        "⚽", "🥅", "🏆", "🏅", "🥇", "🥈", "🥉", "🎽", "👟", "🧤", "📣", "🎯", "🔔", "🎉", "🎊", "🙌",
        "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘",
        "😗", "😙", "😚", "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🥳", "🤩", "😏", "😒",
        "😞", "😔", "😟", "😕", "🙁", "😣", "😖", "😫", "😩", "🥺", "😢", "😭", "😤", "😠", "😡", "🤬",
        "😳", "🥵", "🥶", "😱", "😨", "😰", "😥", "😓", "🤗", "🤔", "🤭", "🤫", "😶", "😐", "😑", "😬",
        "🙄", "😮", "😲", "🥱", "😴", "🤤", "😪", "🤐", "🥴", "🤢", "🤮", "🤧", "😷", "🤒", "🤕", "🤠",
        "👍", "👎", "👌", "✌️", "🤞", "🤟", "🤘", "👏", "👐", "🙏", "💪", "👊", "✊", "🤝", "👋", "🤙",
        "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔", "❣️", "💕", "💞", "💓", "💗", "💖",
        "💘", "💝", "✨", "⭐", "🌟", "💫", "🔥", "💥", "⚡", "💯", "👑", "✅", "❌", "⚠️", "❓", "❗",
        "🐶", "🐱", "🦁", "🐯", "🐻", "🐼", "🐸", "🐵", "🦄", "🍕", "🍔", "🍟", "🌭", "🍿", "🍺", "🍻",
        "🥤", "☕", "🎂", "🍰", "🎁", "🎈", "💬", "💭", "👀", "🤙"
    )

    private val clips = listOf(
        Clip(1, "Zoom: meeting at 3:00 — passcode 8842-116", "Work", listOf("meeting"), "2m", true),
        Clip(2, "mango-kestrel-42-violet", "Personal", listOf("wifi"), "11m", true, true),
        Clip(3, "console.log(JSON.stringify(payload, null, 2))", "Snippets", listOf("snippet"), "26m"),
        Clip(4, "214 Alder Street, Apt 6, Portland OR 97210", "Personal", listOf("address"), "40m", private = true),
        Clip(5, "Order #A-91762 — confirmation sent to inbox", "Work", listOf("order"), "1h"),
        Clip(6, "git commit -m \"fix: clamp swipe threshold\" && git push", "Snippets", listOf("snippet"), "2h"),
    )

    private var cfgCrest = true
    private var cfgCopy = true
    private var cfgEmoji = true
    private var cfgGif = true
    private var cfgMic = true
    private var cfgSettings = true

    private var isClipsMode = false
    private var isEmojiMode = false
    private lateinit var container: FrameLayout
    private lateinit var keyboardView: View
    private lateinit var clipsView: View
    private lateinit var emojiView: View
    private lateinit var clipButton: Button
    private lateinit var backButton: Button
    private lateinit var emojiButton: Button

    override fun onCreateInputView(): View {
        container = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(300)
            )
        }

        keyboardView = createKeyboard()
        clipsView = createClipsView()
        emojiView = createEmojiView()

        container.addView(keyboardView)
        container.addView(clipsView)
        container.addView(emojiView)

        showKeyboard()
        return container
    }

    private fun createKeyboard(): View {
        val layout = LinearLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AndroidColor.WHITE)
        }

        val topBar = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(44)
            )
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(AndroidColor.parseColor("#F5F5F5"))
        }

        clipButton = Button(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(44),
                dpToPx(44)
            )
            text = "📋"
            textSize = 20f
            setBackgroundColor(AndroidColor.TRANSPARENT)
            setOnClickListener { showClips() }
        }
        topBar.addView(clipButton)

        if (cfgEmoji) {
            emojiButton = Button(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    dpToPx(44),
                    dpToPx(44)
                )
                text = "😊"
                textSize = 20f
                setBackgroundColor(AndroidColor.TRANSPARENT)
                setOnClickListener { showEmoji() }
            }
            topBar.addView(emojiButton)
        }

        layout.addView(topBar)

        val keyboardRows = listOf(
            listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
            listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
            listOf("Z", "X", "C", "V", "B", "N", "M")
        )

        keyboardRows.forEach { row ->
            val rowLayout = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dpToPx(45)
                )
                orientation = LinearLayout.HORIZONTAL
                setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
            }

            row.forEach { key ->
                val button = Button(this).apply {
                    val params = LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        1f
                    )
                    params.setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
                    layoutParams = params
                    text = key
                    textSize = 14f
                    setBackgroundColor(AndroidColor.parseColor("#FFFFFF"))
                    setTextColor(AndroidColor.parseColor("#1A1A1A"))
                    setOnClickListener {
                        currentInputConnection?.commitText(key.lowercase(), 1)
                    }
                }
                rowLayout.addView(button)
            }

            layout.addView(rowLayout)
        }

        val bottomRow = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(45)
            )
            orientation = LinearLayout.HORIZONTAL
            setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
        }

        val spaceButton = Button(this).apply {
            val params = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1f
            )
            params.setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
            layoutParams = params
            text = "Space"
            textSize = 12f
            setBackgroundColor(AndroidColor.parseColor("#FFFFFF"))
            setOnClickListener {
                currentInputConnection?.commitText(" ", 1)
            }
        }
        bottomRow.addView(spaceButton)

        val backspaceButton = Button(this).apply {
            val params = LinearLayout.LayoutParams(
                dpToPx(60),
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            params.setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
            layoutParams = params
            text = "⌫"
            textSize = 20f
            setBackgroundColor(AndroidColor.parseColor("#FFFFFF"))
            setOnClickListener {
                currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            }
        }
        bottomRow.addView(backspaceButton)

        layout.addView(bottomRow)

        return layout
    }

    private fun createClipsView(): View {
        val layout = LinearLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AndroidColor.WHITE)
            visibility = View.GONE
        }

        val topBar = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(44)
            )
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(AndroidColor.parseColor("#F5F5F5"))
        }

        val titleText = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1f
            )
            text = "Clips"
            textSize = 16f
            setTextColor(AndroidColor.BLACK)
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dpToPx(14), 0, 0, 0)
        }
        topBar.addView(titleText)

        backButton = Button(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(44),
                dpToPx(44)
            )
            text = "⌨"
            textSize = 20f
            setBackgroundColor(AndroidColor.TRANSPARENT)
            setOnClickListener { showKeyboard() }
        }
        topBar.addView(backButton)

        layout.addView(topBar)

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
        layout.addView(scrollView)

        return layout
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
                showKeyboard()
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

    private fun createEmojiView(): View {
        val layout = LinearLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AndroidColor.WHITE)
            visibility = View.GONE
        }

        val topBar = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(44)
            )
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(AndroidColor.parseColor("#F5F5F5"))
        }

        val titleText = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1f
            )
            text = "Emojis"
            textSize = 16f
            setTextColor(AndroidColor.BLACK)
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dpToPx(14), 0, 0, 0)
        }
        topBar.addView(titleText)

        val backButton = Button(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(44),
                dpToPx(44)
            )
            text = "⌨"
            textSize = 20f
            setBackgroundColor(AndroidColor.TRANSPARENT)
            setOnClickListener { showKeyboard() }
        }
        topBar.addView(backButton)

        layout.addView(topBar)

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val emojiGrid = LinearLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
        }

        var row = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(50)
            )
            orientation = LinearLayout.HORIZONTAL
            setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
        }

        var columnCount = 0
        EMOJIS.forEach { emoji ->
            val button = Button(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    1f
                )
                text = emoji
                textSize = 24f
                setBackgroundColor(AndroidColor.parseColor("#FFFFFF"))
                val params = layoutParams as LinearLayout.LayoutParams
                params.setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
                layoutParams = params
                setOnClickListener {
                    currentInputConnection?.commitText(emoji, 1)
                }
            }
            row.addView(button)
            columnCount++

            if (columnCount == 6) {
                emojiGrid.addView(row)
                row = LinearLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dpToPx(50)
                    )
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
                }
                columnCount = 0
            }
        }

        if (columnCount > 0) {
            emojiGrid.addView(row)
        }

        scrollView.addView(emojiGrid)
        layout.addView(scrollView)

        return layout
    }

    private fun showKeyboard() {
        keyboardView.visibility = View.VISIBLE
        clipsView.visibility = View.GONE
        emojiView.visibility = View.GONE
        isClipsMode = false
        isEmojiMode = false
    }

    private fun showClips() {
        keyboardView.visibility = View.GONE
        clipsView.visibility = View.VISIBLE
        emojiView.visibility = View.GONE
        isClipsMode = true
        isEmojiMode = false
    }

    private fun showEmoji() {
        keyboardView.visibility = View.GONE
        clipsView.visibility = View.GONE
        emojiView.visibility = View.VISIBLE
        isClipsMode = false
        isEmojiMode = true
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
