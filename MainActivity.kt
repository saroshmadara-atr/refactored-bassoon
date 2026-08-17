package com.offlineclipboardtextmanager.app

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Clip(
    val id: Int,
    val text: String,
    val folder: String,
    val tags: List<String>,
    val time: String,
    val pinned: Boolean = false,
    val private: Boolean = false
)

data class DragState(
    val id: Int? = null,
    val dx: Float = 0f,
    val active: Boolean = false,
    val moved: Boolean = false
)

fun categorizeClip(text: String): String {
    return when {
        text.contains(Regex("^https?://")) -> "Work"
        text.contains(Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+")) -> "Work"
        text.contains(Regex("\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}")) -> "Personal"
        text.length > 200 -> "Work"
        else -> "Personal"
    }
}

fun extractTags(text: String): List<String> {
    val tags = mutableListOf<String>()
    if (text.contains(Regex("^https?://"))) tags.add("link")
    if (text.contains(Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+"))) tags.add("email")
    if (text.contains(Regex("\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}"))) tags.add("card")
    if (text.contains(Regex("\\$\\d+")) || text.contains(Regex("\\d+\\.\\d{2}"))) tags.add("money")
    return tags.distinct()
}

fun getTimeString(): String {
    val now = Date()
    val seconds = (System.currentTimeMillis() - (now.time - now.time)) / 1000
    return when {
        seconds < 60 -> "now"
        seconds < 3600 -> "${seconds / 60}m"
        seconds < 86400 -> "${seconds / 3600}h"
        else -> "yesterday"
    }
}

@Composable
fun ClipboardApp(
    accent: Color = Color(0xFF0B0B0B),
    startScreen: String = "Home",
    initialClips: List<Clip> = emptyList(),
    onAddClip: ((String) -> Unit)? = null
) {
    var screen by remember { mutableStateOf(if (startScreen == "Home") "clips" else "onboarding") }
    var tab by remember { mutableStateOf("clips") }
    var folder by remember { mutableStateOf<String?>(null) }
    var detailId by remember { mutableStateOf<Int?>(null) }
    var query by remember { mutableStateOf("") }
    var toastMsg by remember { mutableStateOf("") }
    var toastVisible by remember { mutableStateOf(false) }
    var pinOpen by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var pinAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val unlocked = remember { mutableStateOf(setOf<Int>()) }
    val settings = remember {
        mutableStateOf(
            mapOf(
                "sync" to true,
                "expire" to false,
                "bio" to true,
                "mask" to true
            )
        )
    }

    var clips by remember {
        mutableStateOf(
            if (initialClips.isNotEmpty()) {
                initialClips
            } else {
                listOf(
                    Clip(1, "Zoom: meeting at 3:00 — passcode 8842-116", "Work", listOf("meeting"), "2m", true),
                    Clip(2, "mango-kestrel-42-violet", "Personal", listOf("wifi"), "11m", true, true),
                    Clip(3, "console.log(JSON.stringify(payload, null, 2))", "Snippets", listOf("snippet"), "26m"),
                    Clip(4, "214 Alder Street, Apt 6, Portland OR 97210", "Personal", listOf("address"), "40m", private = true),
                    Clip(5, "Order #A-91762 — confirmation sent to inbox", "Work", listOf("order"), "1h"),
                    Clip(6, "git commit -m \"fix: clamp swipe threshold\" && git push", "Snippets", listOf("snippet"), "2h"),
                    Clip(7, "Reschedule the dentist for next Tuesday afternoon", "Personal", emptyList(), "3h"),
                    Clip(8, "Tracking 1Z999AA10123456784 — arrives Thu", "Work", listOf("tracking"), "yesterday"),
                    Clip(9, "Thanks for the quick turnaround — the deck looks great. Let me know if you need anything before Friday.", "Work", listOf("draft"), "yesterday"),
                )
            }
        )
    }

    var dragState by remember { mutableStateOf(DragState()) }

    fun showToast(msg: String) {
        toastMsg = msg
        toastVisible = true
    }

    fun deleteClip(clip: Clip) {
        clips = clips.filter { it.id != clip.id }
        if (screen == "detail") screen = "clips"
        dragState = DragState()
        showToast("Clip deleted")
    }

    fun togglePin(clip: Clip) {
        clips = clips.map { if (it.id == clip.id) it.copy(pinned = !it.pinned) else it }
        showToast(if (clips.find { it.id == clip.id }?.pinned == true) "Pinned to top" else "Unpinned")
    }

    fun togglePrivate(clip: Clip) {
        val isPrivate = clips.find { it.id == clip.id }?.private == true
        clips = clips.map { if (it.id == clip.id) it.copy(private = !isPrivate) else it }
        if (!isPrivate) {
            unlocked.value = unlocked.value + clip.id
            showToast("Now private")
        } else {
            showToast("Now public")
        }
    }

    fun addClip(clipText: String? = null) {
        val newId = (clips.maxOfOrNull { it.id } ?: 0) + 1
        val (text, folder, tags) = if (clipText != null) {
            Triple(clipText, categorizeClip(clipText), extractTags(clipText))
        } else {
            Triple("https://calendar.app/invite/9fa2c — team sync", "Work", listOf("link"))
        }
        val newClip = Clip(newId, text, folder, tags, getTimeString())
        clips = listOf(newClip) + clips
        showToast("Clip saved")
    }

    fun openClip(clip: Clip) {
        if (clip.private && !unlocked.value.contains(clip.id)) {
            pinAction = { screen = "detail"; detailId = clip.id; unlocked.value = unlocked.value + clip.id }
            pinOpen = true
        } else {
            screen = "detail"
            detailId = clip.id
        }
    }

    LaunchedEffect(toastVisible) {
        if (toastVisible) {
            delay(1500)
            toastVisible = false
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Column(modifier = Modifier.fillMaxSize()) {
            StatusBar()

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.White)
            ) {
                when {
                    screen == "onboarding" -> OnboardingScreen(accent) { screen = "clips"; tab = "clips" }
                    screen == "clips" && tab == "clips" -> ClipsScreen(
                        clips, folder, dragState, { dragState = it }, accent, unlocked.value,
                        { deleteClip(it) }, { togglePin(it) }, { showToast("Copied to clipboard") }, { openClip(it) },
                        settings.value["mask"] as Boolean, { screen = "detail"; detailId = it.id }, { screen = "search"; query = "" },
                        { folder = null }, { folder = it; screen = "clips"; tab = "clips" }
                    )
                    screen == "clips" && tab == "folders" -> FoldersScreen(clips) { folder = it; screen = "clips"; tab = "clips" }
                    screen == "clips" && tab == "settings" -> SettingsScreen(accent, settings.value) { k -> settings.value = settings.value + (k to !(settings.value[k] as Boolean)) }
                    screen == "detail" -> clips.find { it.id == detailId }?.let { clip ->
                        DetailScreen(clip, accent, unlocked.value.contains(clip.id), { deleteClip(clip) }, { togglePin(clip) }, { showToast("Copied to clipboard") }, { togglePrivate(clip) }, { screen = "clips" })
                    }
                    screen == "search" -> SearchScreen(clips, query, { query = it }, accent, unlocked.value, { screen = "detail"; detailId = it.id }, { showToast("Copied to clipboard") }, { screen = "clips"; query = "" }, settings.value["mask"] as Boolean)
                }
            }

            if (screen == "clips") BottomNavigation(tab, accent) { tab = it }
        }

        if (screen == "clips" && tab == "clips") FloatingActionButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            onClick = {
                if (onAddClip != null) {
                    onAddClip("https://calendar.app/invite/9fa2c — team sync")
                } else {
                    addClip()
                }
            },
            containerColor = accent,
            contentColor = Color.White,
            shape = RoundedCornerShape(20.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add clip", modifier = Modifier.size(24.dp))
        }

        if (toastVisible) {
            Toast(toastMsg, Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp))
        }

        if (pinOpen) {
            PinOverlay(pin, { pin = it }, pinError, { pinError = false }, settings.value["bio"] as Boolean, { enteredPin ->
                if (enteredPin == "1234") {
                    pinAction?.invoke()
                    pinOpen = false
                    pin = ""
                    pinAction = null
                    pinError = false
                } else {
                    pinError = true
                    pin = ""
                }
            }, { pinOpen = false; pin = ""; pinAction = null; pinError = false })
        }
    }
}

@Composable
fun StatusBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 26.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("9:30", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(Icons.Default.SignalCellularAlt, "", modifier = Modifier.size(17.dp))
            Icon(Icons.Default.Wifi, "", modifier = Modifier.size(15.dp))
            Icon(Icons.Default.BatteryStd, "", modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun OnboardingScreen(accent: Color, onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(34.dp)
            .padding(top = 52.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .size(66.dp)
                .background(Color(0xFF0B0B0B), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PhonelinkLock, "", tint = Color.White, modifier = Modifier.size(30.dp))
        }
        Spacer(modifier = Modifier.height(34.dp))
        Text(
            "Everything you copy, kept.",
            fontSize = 38.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 40.sp,
            letterSpacing = (-1).sp,
            modifier = Modifier.padding(bottom = 14.dp)
        )
        Text(
            "A quiet clipboard history that saves, organizes and locks the text you paste all day.",
            fontSize = 16.sp,
            color = Color(0xFF6B6B6B),
            lineHeight = 24.sp,
            modifier = Modifier.padding(bottom = 40.dp)
        )
        listOf(
            "Copies save the moment you make them.",
            "Pin, search and file into folders.",
            "Lock private clips behind a passcode."
        ).forEach { bullet ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .border(1.4.dp, Color(0xFFE2E2E2), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ContentCopy, "", modifier = Modifier.size(16.dp))
                }
                Text(bullet, fontSize = 15.sp, lineHeight = 20.sp)
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accent),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Get started", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Color.White)
        }
    }
}

@Composable
fun ClipsScreen(
    clips: List<Clip>,
    folder: String?,
    dragState: DragState,
    onDragStateChange: (DragState) -> Unit,
    accent: Color,
    unlocked: Set<Int>,
    onDelete: (Clip) -> Unit,
    onTogglePin: (Clip) -> Unit,
    onCopy: (Clip) -> Unit,
    onOpenClip: (Clip) -> Unit,
    maskPrivate: Boolean,
    onOpenDetail: (Clip) -> Unit,
    onOpenSearch: () -> Unit,
    onClearFolder: () -> Unit,
    onOpenFolder: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (folder != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClearFolder) {
                    Icon(Icons.Default.ArrowBack, "", modifier = Modifier.size(24.dp))
                }
                Text(folder, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text("Clipboard", fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = onOpenSearch) {
                    Icon(Icons.Default.Search, "")
                }
            }
        }

        val filteredClips = clips.filter { folder?.let { f -> it.folder == f } ?: true }
            .sortedByDescending { it.pinned }

        if (filteredClips.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 120.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ContentPaste, "", modifier = Modifier.size(52.dp), tint = Color(0xFFD2D2D2))
                    Text("Nothing here yet", fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 22.dp))
                    Text("Copy any text on your phone and it lands here automatically.", fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp), textAlign = TextAlign.Center)
                }
            }
        } else {
            LazyColumn {
                items(filteredClips) { clip ->
                    ClipRow(clip, dragState, onDragStateChange, accent, unlocked, onDelete, onTogglePin, onCopy, onOpenDetail, maskPrivate)
                }
            }
        }
    }
}

@Composable
fun ClipRow(
    clip: Clip,
    dragState: DragState,
    onDragStateChange: (DragState) -> Unit,
    accent: Color,
    unlocked: Set<Int>,
    onDelete: (Clip) -> Unit,
    onTogglePin: (Clip) -> Unit,
    onCopy: (Clip) -> Unit,
    onOpenDetail: (Clip) -> Unit,
    maskPrivate: Boolean
) {
    var offset by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .height(120.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset = 0f },
                    onDragEnd = {
                        if (offset < -95f) {
                            onDelete(clip)
                        }
                        offset = 0f
                    }
                ) { change, dragAmount ->
                    offset += dragAmount
                    offset = offset.coerceIn(-150f, 0f)
                }
            }
    ) {
        if (offset < -20) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .align(Alignment.CenterEnd)
                    .padding(end = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Delete, "", tint = Color.White, modifier = Modifier.size(20.dp))
                Text("Delete", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(17.dp, 22.dp)
                .offset(x = offset.dp)
                .clickable { onOpenDetail(clip) }
                .background(Color.White),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                if (clip.pinned) {
                    Icon(Icons.Default.PushPin, "", tint = accent, modifier = Modifier.size(16.dp))
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                val displayText = if (clip.private && !unlocked.contains(clip.id) && maskPrivate) {
                    "•••••••••••••••••"
                } else {
                    clip.text
                }
                Text(
                    displayText,
                    fontSize = 14.5.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 21.sp,
                    color = if (clip.private && !unlocked.contains(clip.id) && maskPrivate) Color(0xFFC4C4C4) else Color(0xFF1A1A1A),
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(9.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 9.dp)
                ) {
                    if (clip.private) {
                        Icon(Icons.Default.Lock, "", modifier = Modifier.size(12.dp), tint = Color(0xFF9A9A9A))
                    }
                    Text(clip.time, fontSize = 12.sp, color = Color(0xFF9A9A9A))
                    Text("·", fontSize = 12.sp, color = Color(0xFF9A9A9A))
                    Text(clip.folder, fontSize = 12.sp, color = Color(0xFF9A9A9A))
                    Text("·", fontSize = 12.sp, color = Color(0xFF9A9A9A))
                    Text("${clip.text.length} chars", fontSize = 12.sp, color = Color(0xFF9A9A9A))
                }
            }

            IconButton(onClick = { onCopy(clip) }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.ContentCopy, "", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun FoldersScreen(clips: List<Clip>, onOpenFolder: (String) -> Unit) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .fillMaxHeight()
        .verticalScroll(rememberScrollState())
        .padding(18.dp, 22.dp)) {
        Text("Folders", fontSize = 30.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 20.dp))
        listOf("Work", "Personal", "Snippets").forEach { folderName ->
            val count = clips.count { it.folder == folderName }
            Button(
                onClick = { onOpenFolder(folderName) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .padding(bottom = 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.3.dp, Color(0xFFEEEEEE))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(Color(0xFFF5F5F5), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Folder, "", modifier = Modifier.size(21.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(folderName, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text("$count clips", fontSize = 13.sp, color = Color(0xFF9A9A9A))
                    }
                    Icon(Icons.Default.ChevronRight, "", modifier = Modifier.size(20.dp), tint = Color(0xFFC4C4C4))
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(accent: Color, settings: Map<String, Boolean>, onToggle: (String) -> Unit) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .fillMaxHeight()
        .verticalScroll(rememberScrollState())
        .padding(18.dp, 22.dp)) {
        Text("Settings", fontSize = 30.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 22.dp))

        Text("GENERAL", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFA0A0A0), modifier = Modifier.padding(4.dp, 0.dp, 0.dp, 10.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 26.dp),
            border = BorderStroke(1.3.dp, Color(0xFFEEEEEE)),
            shape = RoundedCornerShape(16.dp)
        ) {
            SettingRow("Sync across devices", "End-to-end encrypted", settings["sync"] ?: false, { onToggle("sync") }, accent)
            Divider(thickness = 1.dp, color = Color(0xFFF2F2F2))
            SettingRow("Auto-expire old clips", "Delete after 30 days", settings["expire"] ?: false, { onToggle("expire") }, accent)
        }

        Text("PRIVACY", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFA0A0A0), modifier = Modifier.padding(4.dp, 0.dp, 0.dp, 10.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.3.dp, Color(0xFFEEEEEE)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Passcode", fontSize = 15.5.sp, fontWeight = FontWeight.Medium)
                        Text("Set · 4 digits", fontSize = 13.sp, color = Color(0xFF9A9A9A))
                    }
                    Text("Change", fontSize = 13.sp, color = Color(0xFF9A9A9A))
                }
                Divider(thickness = 1.dp, color = Color(0xFFF2F2F2))
                SettingRow("Unlock with fingerprint", "", settings["bio"] ?: false, { onToggle("bio") }, accent, modifier = Modifier.padding(vertical = 16.dp))
                Divider(thickness = 1.dp, color = Color(0xFFF2F2F2))
                SettingRow("Hide private clip previews", "Mask text in the list", settings["mask"] ?: false, { onToggle("mask") }, accent)
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Text("Clipboard · v1.0", fontSize = 12.5.sp, color = Color(0xFFB5B5B5), modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 20.dp))
    }
}

@Composable
fun SettingRow(
    title: String,
    subtitle: String,
    isEnabled: Boolean,
    onToggle: () -> Unit,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp, 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.5.sp, fontWeight = FontWeight.Medium)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, fontSize = 13.sp, color = Color(0xFF9A9A9A), modifier = Modifier.padding(top = 2.dp))
            }
        }
        Switch(
            checked = isEnabled,
            onCheckedChange = { onToggle() },
            modifier = Modifier
                .width(46.dp)
                .height(28.dp),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFE3E3E3)
            )
        )
    }
}

@Composable
fun DetailScreen(
    clip: Clip,
    accent: Color,
    isUnlocked: Boolean,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit,
    onCopy: () -> Unit,
    onTogglePrivate: () -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .fillMaxHeight()
        .verticalScroll(rememberScrollState())
        .padding(14.dp, 22.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 22.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "", modifier = Modifier.size(25.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "", modifier = Modifier.size(21.dp))
            }
        }

        Text(
            clip.text,
            fontSize = 16.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 25.6.sp,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 24.dp)) {
            listOf(clip.folder, clip.time, "${clip.text.length} characters")
                .plus(clip.tags.map { "#$it" })
                .forEach { tag ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF4F4F4),
                        modifier = Modifier.padding(2.dp)
                    ) {
                        Text(tag, fontSize = 12.5.sp, color = Color(0xFF666666), modifier = Modifier.padding(6.dp, 11.dp))
                    }
                }
        }

        Button(
            onClick = onCopy,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accent),
            shape = RoundedCornerShape(15.dp)
        ) {
            Icon(Icons.Default.ContentCopy, "", tint = Color.White, modifier = Modifier
                .size(19.dp)
                .padding(end = 10.dp))
            Text("Copy", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.White)
        }

        Button(
            onClick = onTogglePin,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(top = 12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            border = BorderStroke(1.4.dp, Color(0xFFE8E8E8)),
            shape = RoundedCornerShape(15.dp)
        ) {
            Text(if (clip.pinned) "Unpin clip" else "Pin to top", fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }

        Divider(modifier = Modifier.padding(top = 26.dp, bottom = 22.dp), thickness = 1.dp, color = Color(0xFFF0F0F0))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Private clip", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(
                    if (clip.private) "Hidden in the list and locked behind your passcode." else "Anyone with your phone can read it in the list.",
                    fontSize = 13.5.sp,
                    color = Color(0xFF9A9A9A),
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            Switch(
                checked = clip.private,
                onCheckedChange = { onTogglePrivate() },
                modifier = Modifier
                    .width(46.dp)
                    .height(28.dp),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = accent,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFFE3E3E3)
                )
            )
        }
    }
}

@Composable
fun SearchScreen(
    clips: List<Clip>,
    query: String,
    onQueryChange: (String) -> Unit,
    accent: Color,
    unlocked: Set<Int>,
    onOpenDetail: (Clip) -> Unit,
    onCopy: (Clip) -> Unit,
    onBack: () -> Unit,
    maskPrivate: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp, 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "", modifier = Modifier.size(24.dp))
            }
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search clips") },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .background(Color(0xFFF4F4F4), RoundedCornerShape(14.dp)),
                leadingIcon = { Icon(Icons.Default.Search, "", tint = Color(0xFF9A9A9A)) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFFF4F4F4),
                    unfocusedContainerColor = Color(0xFFF4F4F4),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
        }

        if (query.isEmpty()) {
            Text("RECENT", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFA0A0A0), modifier = Modifier.padding(4.dp, 14.dp, 0.dp, 14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
                listOf("wifi", "snippet", "Work", "order").forEach { tag ->
                    Button(
                        onClick = { onQueryChange(tag) },
                        modifier = Modifier
                            .padding(4.dp)
                            .height(36.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        border = BorderStroke(1.3.dp, Color(0xFFECECEC)),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(tag, fontSize = 14.sp)
                    }
                }
            }
        }

        val searchResults = if (query.isNotEmpty()) {
            clips.filter {
                it.text.contains(query, ignoreCase = true) ||
                        it.folder.contains(query, ignoreCase = true) ||
                        it.tags.any { tag -> tag.contains(query, ignoreCase = true) }
            }
        } else {
            emptyList()
        }

        if (query.isNotEmpty() && searchResults.isEmpty()) {
            Text("No clips match \"$query\".", fontSize = 15.sp, color = Color(0xFF9A9A9A), modifier = Modifier.padding(30.dp, 80.dp))
        } else {
            LazyColumn {
                items(searchResults) { clip ->
                    SearchClipRow(clip, unlocked, onOpenDetail, onCopy, maskPrivate)
                }
            }
        }
    }
}

@Composable
fun SearchClipRow(clip: Clip, unlocked: Set<Int>, onOpenDetail: (Clip) -> Unit, onCopy: (Clip) -> Unit, maskPrivate: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenDetail(clip) }
            .padding(16.dp, 4.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val displayText = if (clip.private && !unlocked.contains(clip.id) && maskPrivate) "•••••••••••••••••" else clip.text
                Text(displayText, fontSize = 14.sp, fontFamily = FontFamily.Monospace, lineHeight = 21.sp, maxLines = 1)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    Text(clip.folder, fontSize = 12.sp, color = Color(0xFF9A9A9A))
                    Text("·", fontSize = 12.sp, color = Color(0xFF9A9A9A))
                    Text(clip.time, fontSize = 12.sp, color = Color(0xFF9A9A9A))
                }
            }
            IconButton(onClick = { onCopy(clip) }, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Default.ContentCopy, "", modifier = Modifier.size(17.dp))
            }
        }
        Divider(thickness = 1.dp, color = Color(0xFFF2F2F2))
    }
}

@Composable
fun BottomNavigation(currentTab: String, accent: Color, onTabChange: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Divider(thickness = 1.dp, color = Color(0xFFF0F0F0))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .background(Color.White)
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("clips" to Icons.Default.ContentPaste, "folders" to Icons.Default.Folder, "settings" to Icons.Default.Settings)
                .forEach { (tab, icon) ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onTabChange(tab) },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(icon, "", tint = if (tab == currentTab) accent else Color(0xFFB4B4B4), modifier = Modifier.size(24.dp))
                        Text(
                            tab.replaceFirstChar { it.uppercase() },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (tab == currentTab) accent else Color(0xFFB4B4B4)
                        )
                    }
                }
        }
    }
}

@Composable
fun Toast(message: String, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(initialOffsetY = { it / 2 }),
        exit = slideOutVertically()
    ) {
        Row(
            modifier = modifier
                .background(Color(0xFF111111), RoundedCornerShape(13.dp))
                .padding(13.dp, 20.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Check, "", tint = Color.White, modifier = Modifier.size(17.dp))
            Text(message, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
        }
    }
}

@Composable
fun PinOverlay(
    pin: String,
    onPinChange: (String) -> Unit,
    pinError: Boolean,
    onErrorDismiss: () -> Unit,
    bioEnabled: Boolean,
    onPinSubmit: (String) -> Unit,
    onCancel: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, "", modifier = Modifier.size(24.dp))
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .border(1.5.dp, Color(0xFFEEEEEE), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Lock, "", modifier = Modifier.size(26.dp))
                }

                Text(
                    if (pinError) "Try again" else "Enter passcode",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 24.dp, bottom = 6.dp)
                )
                Text(
                    if (pinError) "That passcode was incorrect" else "Unlock to view this private clip",
                    fontSize = 14.sp,
                    color = if (pinError) Color(0xFFB00020) else Color(0xFF9A9A9A),
                    modifier = Modifier.padding(bottom = 30.dp)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(bottom = 44.dp)
                ) {
                    repeat(4) { i ->
                        Box(
                            modifier = Modifier
                                .size(15.dp)
                                .background(
                                    if (i < pin.length) Color(0xFF0B0B0B) else Color.Transparent,
                                    CircleShape
                                )
                                .border(
                                    1.6.dp,
                                    if (i < pin.length) Color(0xFF0B0B0B) else Color(0xFFD5D5D5),
                                    CircleShape
                                )
                        )
                    }
                }

                Column(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    repeat(3) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.padding(bottom = 14.dp)) {
                            repeat(3) { col ->
                                val num = (row * 3 + col + 1).toString()
                                Button(
                                    onClick = { if (pin.length < 4) onPinChange(pin + num) },
                                    modifier = Modifier.size(74.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF6F6F6)),
                                    shape = CircleShape
                                ) {
                                    Text(num, fontSize = 28.sp, color = Color(0xFF0B0B0B))
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        if (bioEnabled) {
                            Button(
                                onClick = { onPinSubmit("biometric") },
                                modifier = Modifier.size(74.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                            ) {
                                Icon(Icons.Default.Fingerprint, "", modifier = Modifier.size(28.dp))
                            }
                        } else {
                            Box(modifier = Modifier.size(74.dp))
                        }
                        Button(
                            onClick = { if (pin.length < 4) onPinChange(pin + "0") },
                            modifier = Modifier.size(74.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF6F6F6)),
                            shape = CircleShape
                        ) {
                            Text("0", fontSize = 28.sp, color = Color(0xFF0B0B0B))
                        }
                        Button(
                            onClick = { if (pin.isNotEmpty()) onPinChange(pin.dropLast(1)) },
                            modifier = Modifier.size(74.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                        ) {
                            Icon(Icons.Default.Backspace, "", modifier = Modifier.size(26.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
                Text("Demo passcode — 1234", fontSize = 12.5.sp, color = Color(0xFFC0C0C0), modifier = Modifier.padding(bottom = 30.dp))
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    private var lastClipboardText = ""
    private val clipboardManager by lazy { getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    private lateinit var clipsState: MutableState<List<Clip>>
    private lateinit var addClipCallback: (String) -> Unit

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            clipsState = remember {
                mutableStateOf(
                    listOf(
                        Clip(1, "Zoom: meeting at 3:00 — passcode 8842-116", "Work", listOf("meeting"), "2m", true),
                        Clip(2, "mango-kestrel-42-violet", "Personal", listOf("wifi"), "11m", true, true),
                        Clip(3, "console.log(JSON.stringify(payload, null, 2))", "Snippets", listOf("snippet"), "26m"),
                        Clip(4, "214 Alder Street, Apt 6, Portland OR 97210", "Personal", listOf("address"), "40m", private = true),
                        Clip(5, "Order #A-91762 — confirmation sent to inbox", "Work", listOf("order"), "1h"),
                        Clip(6, "git commit -m \"fix: clamp swipe threshold\" && git push", "Snippets", listOf("snippet"), "2h"),
                        Clip(7, "Reschedule the dentist for next Tuesday afternoon", "Personal", emptyList(), "3h"),
                        Clip(8, "Tracking 1Z999AA10123456784 — arrives Thu", "Work", listOf("tracking"), "yesterday"),
                        Clip(9, "Thanks for the quick turnaround — the deck looks great. Let me know if you need anything before Friday.", "Work", listOf("draft"), "yesterday"),
                    )
                )
            }
            addClipCallback = { text ->
                val newId = (clipsState.value.maxOfOrNull { it.id } ?: 0) + 1
                val folder = categorizeClip(text)
                val newClip = Clip(
                    id = newId,
                    text = text,
                    folder = folder,
                    tags = extractTags(text),
                    time = getTimeString(),
                    pinned = false,
                    private = false
                )
                clipsState.value = listOf(newClip) + clipsState.value
            }
            MaterialTheme {
                ClipboardApp(accent = Color(0xFF0B0B0B), startScreen = "Home", initialClips = clipsState.value) { clipText ->
                    addClipCallback(clipText)
                }
            }
        }
        setupClipboardListener()
    }

    private fun setupClipboardListener() {
        clipboardManager.addPrimaryClipChangedListener {
            val clip = clipboardManager.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString() ?: return@addPrimaryClipChangedListener

                if (text.isNotEmpty() && text != lastClipboardText) {
                    lastClipboardText = text
                    if (::addClipCallback.isInitialized) {
                        addClipCallback(text)
                    }
                }
            }
        }
    }

}
