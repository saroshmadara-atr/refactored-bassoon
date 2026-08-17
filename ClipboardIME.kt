package com.offlineclipboardtextmanager.app

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.KeyboardView
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class ClipboardIME : InputMethodService() {
    private val clipboardManager by lazy { getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager }
    private var clips = mutableStateOf(emptyList<Clip>())

    override fun onCreateInputView(): View {
        val composeView = ComposeView(this).apply {
            setContent {
                MaterialTheme {
                    KeyboardView(clips.value, { text ->
                        val inputConnection = currentInputConnection
                        inputConnection?.commitText(text, text.length)
                    })
                }
            }
        }

        loadClips()
        return composeView
    }

    private fun loadClips() {
        val prefs = getSharedPreferences("clips", Context.MODE_PRIVATE)
        val clipText = prefs.getString("clips_json", null)
        clips.value = listOf(
            Clip(1, "Zoom: meeting at 3:00 — passcode 8842-116", "Work", listOf("meeting"), "2m", true),
            Clip(2, "mango-kestrel-42-violet", "Personal", listOf("wifi"), "11m", true, true),
            Clip(3, "console.log(JSON.stringify(payload, null, 2))", "Snippets", listOf("snippet"), "26m"),
            Clip(4, "214 Alder Street, Apt 6, Portland OR 97210", "Personal", listOf("address"), "40m", private = true),
            Clip(5, "Order #A-91762 — confirmation sent to inbox", "Work", listOf("order"), "1h"),
            Clip(6, "git commit -m \"fix: clamp swipe threshold\" && git push", "Snippets", listOf("snippet"), "2h"),
        )
    }

    override fun onStartInputView(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(attribute, restarting)
        loadClips()
    }
}

@Composable
fun KeyboardView(clips: List<Clip>, onClipSelected: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5))
    ) {
        Text(
            "Clips",
            fontSize = 14.sp,
            modifier = Modifier.padding(12.dp, 12.dp, 12.dp, 8.dp)
        )

        if (clips.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No clips yet", fontSize = 13.sp, color = Color(0xFF9A9A9A))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 250.dp)
            ) {
                items(clips) { clip ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onClipSelected(clip.text) }
                            .background(Color.White)
                            .padding(12.dp, 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                clip.text.take(40) + if (clip.text.length > 40) "..." else "",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                            Text(
                                clip.folder,
                                fontSize = 10.sp,
                                color = Color(0xFF9A9A9A)
                            )
                        }
                        Icon(Icons.Default.ContentCopy, "", modifier = Modifier.size(16.dp), tint = Color(0xFF9A9A9A))
                    }
                    Divider(thickness = 0.5.dp, color = Color(0xFFEEEEEE))
                }
            }
        }
    }
}
