package com.offlineclipboardtextmanager.app

import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
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

class FloatingPanelService : Service() {
    private val windowManager by lazy { getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    private val clipboardManager by lazy { getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    private var floatingView: ComposeView? = null
    private var clips = mutableStateOf(emptyList<Clip>())
    private var isExpanded = mutableStateOf(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "show" -> showFloatingPanel()
            "hide" -> hideFloatingPanel()
        }
        return START_STICKY
    }

    private fun showFloatingPanel() {
        if (floatingView != null) return

        loadClips()

        floatingView = ComposeView(this).apply {
            setContent {
                MaterialTheme {
                    FloatingPanelUI(
                        clips.value,
                        isExpanded.value,
                        { isExpanded.value = it },
                        { text ->
                            val clip = ClipData.newPlainText("clipboard", text)
                            clipboardManager.setPrimaryClip(clip)
                        },
                        { hideFloatingPanel() }
                    )
                }
            }
        }

        val params = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams(
                300,
                if (isExpanded.value) 500 else 80,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                android.graphics.PixelFormat.TRANSLUCENT
            )
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams(
                300,
                if (isExpanded.value) 500 else 80,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                android.graphics.PixelFormat.TRANSLUCENT
            )
        }

        params.gravity = Gravity.TOP or Gravity.END
        params.x = 20
        params.y = 100

        windowManager.addView(floatingView, params)
    }

    private fun hideFloatingPanel() {
        floatingView?.let { windowManager.removeView(it) }
        floatingView = null
        stopSelf()
    }

    private fun loadClips() {
        clips.value = listOf(
            Clip(1, "Zoom: meeting at 3:00 — passcode 8842-116", "Work", listOf("meeting"), "2m", true),
            Clip(2, "mango-kestrel-42-violet", "Personal", listOf("wifi"), "11m", true, true),
            Clip(3, "console.log(JSON.stringify(payload, null, 2))", "Snippets", listOf("snippet"), "26m"),
            Clip(4, "214 Alder Street, Apt 6, Portland OR 97210", "Personal", listOf("address"), "40m", private = true),
            Clip(5, "Order #A-91762 — confirmation sent to inbox", "Work", listOf("order"), "1h"),
            Clip(6, "git commit -m \"fix: clamp swipe threshold\" && git push", "Snippets", listOf("snippet"), "2h"),
        )
    }
}

@Composable
fun FloatingPanelUI(
    clips: List<Clip>,
    isExpanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    onClipSelected: (String) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.ContentCopy,
                "",
                modifier = Modifier.size(20.dp),
                tint = Color(0xFF0B0B0B)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = { onExpandChange(!isExpanded) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        "",
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, "", modifier = Modifier.size(18.dp))
                }
            }
        }

        if (isExpanded) {
            Divider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 350.dp)
            ) {
                items(clips.sortedByDescending { it.pinned }) { clip ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onClipSelected(clip.text) }
                            .padding(vertical = 6.dp)
                    ) {
                        Text(
                            clip.text.take(50) + if (clip.text.length > 50) "..." else "",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 3.dp)
                        ) {
                            if (clip.pinned) {
                                Icon(Icons.Default.PushPin, "", modifier = Modifier.size(10.dp), tint = Color(0xFF0B0B0B))
                            }
                            Text(clip.folder, fontSize = 9.sp, color = Color(0xFF9A9A9A))
                        }
                    }
                }
            }
        }
    }
}
