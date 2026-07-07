package com.nishparadox.smriti

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import com.nishparadox.smriti.apps.AppWhitelist
import com.nishparadox.smriti.capture.CaptureService
import com.nishparadox.smriti.notes.DriveSync
import com.nishparadox.smriti.notes.Snip
import com.nishparadox.smriti.notes.SnipStatus
import com.nishparadox.smriti.notes.SnipStore
import com.nishparadox.smriti.settings.Settings
import com.nishparadox.smriti.trigger.FloatingBubbleService
import com.nishparadox.smriti.ui.SmritiTheme
import com.nishparadox.smriti.ui.ThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = Settings(this)
        val mpm = getSystemService(MediaProjectionManager::class.java)
        SnipStore.ensureLoaded(this)
        DriveSync.init(this, settings.driveRoot)
        DriveSync.pull()

        setContent {
            var themeMode by remember { mutableStateOf(ThemeMode.from(settings.themeMode)) }
            SmritiTheme(themeMode) {
                var screen by remember { mutableStateOf("main") }
                var running by remember { mutableStateOf(CaptureService.instance != null) }
                DisposableEffect(Unit) {
                    CaptureService.onRunningChanged = { active -> running = active }
                    onDispose { CaptureService.onRunningChanged = null }
                }
                var pre by remember { mutableStateOf(settings.preRoll.toFloat()) }
                var tot by remember { mutableStateOf(settings.total.toFloat()) }
                var showPicker by remember { mutableStateOf(false) }
                var detailSnip by remember { mutableStateOf<Snip?>(null) }
                var driveConnected by remember { mutableStateOf(settings.driveRoot.isNotEmpty()) }
                val selectedIds = remember { mutableStateListOf<Long>() }
                val selected = remember {
                    mutableStateListOf<String>().apply { addAll(settings.whitelist) }
                }

                val treePicker = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocumentTree()
                ) { uri ->
                    if (uri != null && DriveSync.setup(uri)) {
                        settings.driveRoot = uri.toString()
                        DriveSync.pull()
                        DriveSync.syncMine()   // push existing notes right away
                        driveConnected = true
                    }
                }
                val notifPerm = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { }
                val projection = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) { res ->
                    if (res.resultCode != 0 && res.data != null) {
                        settings.preRoll = pre.toInt()
                        settings.total = tot.toInt()
                        settings.whitelist = selected.toSet()
                        val uids = AppWhitelist.uidsFor(this@MainActivity, selected.toSet())
                        startForegroundService(
                            Intent(this@MainActivity, CaptureService::class.java).apply {
                                putExtra("code", res.resultCode)
                                putExtra("data", res.data)
                                putExtra("preRoll", pre.toInt())
                                putExtra("total", tot.toInt())
                                putExtra("uids", uids)
                                putExtra(
                                    "sourceLabel",
                                    if (selected.size == 1) labelOf(this@MainActivity, selected.first()) else ""
                                )
                            }
                        )
                        startService(Intent(this@MainActivity, FloatingBubbleService::class.java))
                        running = true
                    }
                }

                fun startSession() {
                    if (Build.VERSION.SDK_INT >= 33) {
                        notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                    projection.launch(mpm.createScreenCaptureIntent())
                }
                fun stopSession() {
                    stopService(Intent(this@MainActivity, CaptureService::class.java))
                    stopService(Intent(this@MainActivity, FloatingBubbleService::class.java))
                    running = false
                }

                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (screen == "main") {
                        val canOverlay = AndroidSettings.canDrawOverlays(this@MainActivity)
                        Column(Modifier.fillMaxSize().systemBarsPadding().padding(20.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                IconButton(onClick = { screen = "settings" }) {
                                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                                }
                            }

                            Column(
                                Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Spacer(Modifier.height(8.dp))
                                Text("स्मृति", fontSize = 44.sp, color = MaterialTheme.colorScheme.primary)
                                Text(
                                    "smriti · capture what you hear",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    if (running) "● Listening — tap the स्म bubble" else "Idle",
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(Modifier.height(12.dp))
                                if (!running) {
                                    Button(
                                        onClick = { startSession() },
                                        enabled = selected.isNotEmpty() && canOverlay,
                                        modifier = Modifier.fillMaxWidth(0.8f).height(52.dp)
                                    ) { Text("▶  Start listening", fontSize = 18.sp) }
                                } else {
                                    Button(
                                        onClick = { stopSession() },
                                        modifier = Modifier.fillMaxWidth(0.8f).height(52.dp)
                                    ) { Text("■  Stop", fontSize = 18.sp) }
                                }
                                if (!canOverlay) {
                                    Spacer(Modifier.height(10.dp))
                                    OutlinedButton(onClick = {
                                        startActivity(
                                            Intent(
                                                AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:$packageName")
                                            )
                                        )
                                    }) { Text("Grant 'Draw over other apps'") }
                                }
                                if (selected.isEmpty()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "Pick an app to watch in Settings",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp
                                    )
                                }
                            }

                            Spacer(Modifier.height(20.dp))
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                if (selectedIds.isEmpty()) {
                                    Text("Recent", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    Text("${selectedIds.size} selected", fontSize = 16.sp)
                                    Spacer(Modifier.weight(1f))
                                    IconButton(onClick = {
                                        val text = SnipStore.snips.filter { it.id in selectedIds }
                                            .joinToString("\n\n---\n\n") { it.text }
                                        shareText(this@MainActivity, text)
                                    }) { Icon(Icons.Filled.Share, contentDescription = "Share selected") }
                                    IconButton(onClick = {
                                        selectedIds.toList().forEach { SnipStore.delete(it) }
                                        selectedIds.clear()
                                    }) { Icon(Icons.Filled.Delete, contentDescription = "Delete selected") }
                                    IconButton(onClick = { selectedIds.clear() }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Clear selection")
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            if (SnipStore.snips.isEmpty()) {
                                Text(
                                    "Your smaran will appear here.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp
                                )
                            } else {
                                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                                    items(SnipStore.snips, key = { it.id }) { snip ->
                                        val selectionMode = selectedIds.isNotEmpty()
                                        val dismiss = rememberSwipeToDismissBoxState(
                                            positionalThreshold = { it * 0.5f },   // friction: past halfway
                                            confirmValueChange = { v ->
                                                if (v == SwipeToDismissBoxValue.EndToStart) {
                                                    SnipStore.delete(snip.id); true
                                                } else false
                                            }
                                        )
                                        SwipeToDismissBox(
                                            state = dismiss,
                                            enableDismissFromStartToEnd = false,
                                            enableDismissFromEndToStart = !selectionMode,
                                            backgroundContent = {
                                                Row(
                                                    Modifier.fillMaxSize()
                                                        .background(MaterialTheme.colorScheme.errorContainer)
                                                        .padding(horizontal = 24.dp),
                                                    horizontalArrangement = Arrangement.End,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        Icons.Filled.Delete, contentDescription = "Delete",
                                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                                    )
                                                }
                                            }
                                        ) {
                                            SnipRow(
                                                snip = snip,
                                                selected = snip.id in selectedIds,
                                                selectionMode = selectionMode,
                                                onOpen = { detailSnip = snip },
                                                onToggle = {
                                                    if (snip.id in selectedIds) selectedIds.remove(snip.id)
                                                    else selectedIds.add(snip.id)
                                                },
                                                onLongPress = { if (snip.id !in selectedIds) selectedIds.add(snip.id) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        detailSnip?.let { s ->
                            SnipDetailDialog(
                                snip = s,
                                onDismiss = { detailSnip = null },
                                onDelete = { SnipStore.delete(s.id); detailSnip = null }
                            )
                        }
                    } else {
                        Column(
                            Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = {
                                    settings.preRoll = pre.toInt()
                                    settings.total = tot.toInt()
                                    settings.whitelist = selected.toSet()
                                    screen = "main"
                                }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                                Text("Settings", fontSize = 20.sp)
                            }
                            Spacer(Modifier.height(8.dp))

                            Text("Theme", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row {
                                listOf(ThemeMode.SYSTEM, ThemeMode.DARK, ThemeMode.LIGHT).forEach { m ->
                                    val label = m.name.lowercase().replaceFirstChar { it.uppercase() }
                                    if (m == themeMode) {
                                        Button(onClick = {}, modifier = Modifier.padding(end = 8.dp)) { Text(label) }
                                    } else {
                                        OutlinedButton(
                                            onClick = { themeMode = m; settings.themeMode = m.name.lowercase() },
                                            modifier = Modifier.padding(end = 8.dp)
                                        ) { Text(label) }
                                    }
                                }
                            }
                            Spacer(Modifier.height(16.dp))

                            Text("Pre-roll (N): ${pre.toInt()} s")
                            Slider(value = pre, onValueChange = { pre = it }, valueRange = 1f..15f)
                            Text("Total clip (K): ${tot.toInt()} s")
                            Slider(
                                value = tot,
                                onValueChange = { tot = it.coerceAtLeast(pre + 1f) },
                                valueRange = 5f..60f
                            )
                            Spacer(Modifier.height(20.dp))

                            Text("Watched apps", fontSize = 16.sp)
                            Text(
                                "Snips only capture audio from these apps.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            if (selected.isEmpty()) {
                                Text("None selected yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                selected.toList().forEach { pkg ->
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AppIcon(pkg, 36)
                                        Spacer(Modifier.width(12.dp))
                                        Text(labelOf(this@MainActivity, pkg), Modifier.weight(1f))
                                        IconButton(onClick = { selected.remove(pkg) }) {
                                            Icon(Icons.Filled.Close, contentDescription = "Remove")
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { showPicker = true }) { Text("＋  Choose apps") }

                            Spacer(Modifier.height(24.dp))
                            Text("Google Drive sync", fontSize = 16.sp)
                            Text(
                                "Notes sync as plain files to a Drive folder — no account or API. Pick any folder once; Smriti makes its own smriti/ subfolder inside it.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            if (driveConnected) {
                                Text("Connected ✓", color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(6.dp))
                                Row {
                                    OutlinedButton(
                                        onClick = { DriveSync.pull() },
                                        modifier = Modifier.padding(end = 8.dp)
                                    ) { Text("Sync now") }
                                    OutlinedButton(onClick = {
                                        DriveSync.disable(); settings.driveRoot = ""; driveConnected = false
                                    }) { Text("Disconnect") }
                                }
                            } else {
                                Button(onClick = { treePicker.launch(null) }) { Text("Connect Drive folder") }
                            }
                            Spacer(Modifier.height(24.dp))
                        }

                        if (showPicker) {
                            AppPickerDialog(selected = selected, onDismiss = { showPicker = false })
                        }
                    }
                }
            }
        }
    }
}

private fun labelOf(ctx: Context, pkg: String): String = runCatching {
    ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
}.getOrDefault(pkg)

private fun shareText(ctx: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    ctx.startActivity(Intent.createChooser(send, "Share snip").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SnipRow(
    snip: Snip,
    selected: Boolean,
    selectionMode: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
) {
    val ctx = LocalContext.current
    val badge: String
    val preview: String
    when (snip.status) {
        SnipStatus.RECORDING -> { badge = "●"; preview = "Recording… (up to ${snip.durationS}s)" }
        SnipStatus.TRANSCRIBING -> { badge = "⏳"; preview = "Processing · ${snip.durationS}s of audio…" }
        SnipStatus.FAILED -> { badge = "⚠"; preview = snip.text.ifBlank { "Failed" } }
        SnipStatus.DONE -> { badge = ""; preview = snip.text.ifBlank { "(no speech detected)" } }
    }
    Row(
        Modifier.fillMaxWidth()
            .combinedClickable(
                onClick = { if (selectionMode) onToggle() else onOpen() },
                onLongClick = onLongPress
            )
            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        val marker = if (selected) "☑" else badge
        if (marker.isNotEmpty()) Text(marker, Modifier.padding(end = 10.dp))
        Column(Modifier.weight(1f)) {
            Text(preview, maxLines = 4, color = MaterialTheme.colorScheme.onBackground)
            Text(
                buildString {
                    if (snip.source.isNotBlank()) append("${snip.source} · ")
                    append(DateUtils.getRelativeTimeSpanString(snip.createdAt))
                    if (snip.durationS > 0) append(" · ${snip.durationS}s")
                },
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!selectionMode && snip.status == SnipStatus.DONE && snip.text.isNotBlank()) {
            IconButton(onClick = { shareText(ctx, snip.text) }) {
                Icon(Icons.Filled.Share, contentDescription = "Share")
            }
        }
    }
}

@Composable
private fun AppIcon(pkg: String, sizeDp: Int) {
    val ctx = LocalContext.current
    val bmp = remember(pkg) {
        runCatching { ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }
            .getOrNull()
    }
    if (bmp != null) Image(bmp, contentDescription = null, modifier = Modifier.size(sizeDp.dp))
    else Spacer(Modifier.size(sizeDp.dp))
}

@Composable
private fun AppPickerDialog(selected: SnapshotStateList<String>, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val apps = remember { AppWhitelist.installedMediaApps(ctx) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Media apps", fontSize = 18.sp)
                Text(
                    "Only apps that play media are shown.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                if (apps.isEmpty()) {
                    Text("No media apps found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(apps) { (pkg, label) ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                if (selected.contains(pkg)) selected.remove(pkg) else selected.add(pkg)
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIcon(pkg, 40)
                            Spacer(Modifier.width(12.dp))
                            Text(label, Modifier.weight(1f))
                            Checkbox(
                                checked = selected.contains(pkg),
                                onCheckedChange = {
                                    if (it) selected.add(pkg) else selected.remove(pkg)
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Done") }
                }
            }
        }
    }
}

@Composable
private fun SnipDetailDialog(snip: Snip, onDismiss: () -> Unit, onDelete: () -> Unit) {
    val ctx = LocalContext.current
    var edited by remember(snip.id) { mutableStateOf(snip.text) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    DateUtils.getRelativeTimeSpanString(snip.createdAt).toString() +
                        (if (snip.durationS > 0) " · ${snip.durationS}s" else ""),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = edited,
                    onValueChange = { edited = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 360.dp),
                    label = { Text("Transcript") }
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { shareText(ctx, edited) }) { Text("Share") }
                    Spacer(Modifier.width(4.dp))
                    Button(onClick = {
                        SnipStore.update(snip.id) { it.copy(text = edited) }
                        onDismiss()
                    }) { Text("Save") }
                }
            }
        }
    }
}
