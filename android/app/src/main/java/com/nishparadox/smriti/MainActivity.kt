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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import androidx.documentfile.provider.DocumentFile
import com.nishparadox.smriti.apps.AppWhitelist
import com.nishparadox.smriti.capture.CaptureService
import com.nishparadox.smriti.notes.DeviceId
import com.nishparadox.smriti.notes.DriveSync
import com.nishparadox.smriti.notes.SmaranType
import com.nishparadox.smriti.notes.Snip
import com.nishparadox.smriti.notes.SnipStatus
import com.nishparadox.smriti.notes.SnipStore
import com.nishparadox.smriti.settings.Settings
import com.nishparadox.smriti.transcribe.ModelManager
import com.nishparadox.smriti.trigger.FloatingBubbleService
import com.nishparadox.smriti.ui.SmritiTheme
import com.nishparadox.smriti.ui.ThemeMode
import com.nishparadox.smriti.update.UpdateChecker
import java.time.Instant
import java.time.Month
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = Settings(this)
        val appVersion = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }
            .getOrNull() ?: "?"
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
                val snackbar = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                fun deleteWithUndo(snip: Snip) {
                    SnipStore.delete(snip.id)
                    scope.launch {
                        val r = snackbar.showSnackbar("Deleted", actionLabel = "Undo", duration = SnackbarDuration.Short)
                        if (r == SnackbarResult.ActionPerformed) SnipStore.restore(snip)
                    }
                }
                fun checkForUpdates(silent: Boolean) {
                    scope.launch {
                        when (val r = UpdateChecker.check(appVersion)) {
                            is UpdateChecker.Result.Available -> {
                                val res = snackbar.showSnackbar(
                                    "Update available — v${r.version}", actionLabel = "Get",
                                    duration = SnackbarDuration.Long
                                )
                                if (res == SnackbarResult.ActionPerformed)
                                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.url)))
                            }
                            UpdateChecker.Result.UpToDate ->
                                if (!silent) snackbar.showSnackbar("You're on the latest version")
                            UpdateChecker.Result.Failed ->
                                if (!silent) snackbar.showSnackbar("Couldn't check for updates")
                        }
                    }
                }
                LaunchedEffect(Unit) { checkForUpdates(silent = true) }
                var pre by remember { mutableStateOf(settings.preRoll.toFloat()) }
                var tot by remember { mutableStateOf(settings.total.toFloat()) }
                var showPicker by remember { mutableStateOf(false) }
                var detailSnip by remember { mutableStateOf<Snip?>(null) }
                var query by remember { mutableStateOf("") }
                var typeFilter by remember { mutableStateOf<SmaranType?>(null) }
                var fromMonth by remember { mutableStateOf<YearMonth?>(null) }
                var toMonth by remember { mutableStateOf<YearMonth?>(YearMonth.now()) }   // "To" defaults to current month
                var datePickerFor by remember { mutableStateOf<String?>(null) }   // "from" | "to" | null
                var audioEnabled by remember { mutableStateOf(settings.audioTranscription) }
                var modelPct by remember { mutableStateOf<Int?>(null) }   // non-null = downloading %
                var modelReady by remember { mutableStateOf(ModelManager.isPresent(this@MainActivity)) }
                var showNewNote by remember { mutableStateOf(false) }
                var driveConnected by remember { mutableStateOf(settings.driveRoot.isNotEmpty()) }
                val driveFolder = remember(driveConnected) {
                    if (driveConnected) runCatching {
                        DocumentFile.fromTreeUri(this@MainActivity, Uri.parse(settings.driveRoot))?.name
                    }.getOrNull() else null
                }
                val selectedIds = remember { mutableStateListOf<Long>() }
                val selected = remember {
                    mutableStateListOf<String>().apply { addAll(settings.whitelist) }
                }
                val listFields = remember {
                    mutableStateListOf<String>().apply { addAll(settings.listFields) }
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

                Box(Modifier.fillMaxSize()) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (screen == "main") {
                        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                            // Header: identity + settings. Listen lives in the floating button.
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "स्मृति",
                                        fontSize = 32.sp, lineHeight = 44.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        "capture what matters",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp
                                    )
                                }
                                IconButton(onClick = { showNewNote = true }) {
                                    Icon(Icons.Filled.Add, contentDescription = "New note")
                                }
                                IconButton(onClick = { screen = "settings" }) {
                                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                                }
                            }
                            if (showNewNote) {
                                NewNoteDialog(
                                    onDismiss = { showNewNote = false },
                                    onSave = { txt ->
                                        val id = System.currentTimeMillis()
                                        SnipStore.add(
                                            Snip(
                                                id = id, createdAt = id, status = SnipStatus.DONE,
                                                type = SmaranType.NOTE, text = txt, source = "You",
                                                device = DeviceId.of(this@MainActivity),
                                            )
                                        )
                                        DriveSync.syncMine()
                                        showNewNote = false
                                    }
                                )
                            }
                            if (running) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "● Listening — tap the स्म bubble to snip",
                                    color = MaterialTheme.colorScheme.primary, fontSize = 13.sp
                                )
                            }

                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))

                            if (SnipStore.snips.isEmpty()) {
                                Column(
                                    Modifier.weight(1f).fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        "Nothing captured yet",
                                        color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        if (audioEnabled)
                                            "Highlight on the web, share text, or tap ▶ to listen —\nyour smaran show up here."
                                        else
                                            "Highlight on the web or share text —\nyour smaran show up here.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 13.sp, lineHeight = 20.sp, textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                Spacer(Modifier.height(14.dp))
                                if (selectedIds.isNotEmpty()) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
                                } else {
                                    OutlinedTextField(
                                        value = query,
                                        onValueChange = { query = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text("Search smaran") },
                                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                        singleLine = true
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FilterChip(
                                            selected = typeFilter == null,
                                            onClick = { typeFilter = null },
                                            label = { Text("All") }
                                        )
                                        listOf(SmaranType.AUDIO, SmaranType.WEB, SmaranType.TEXT, SmaranType.NOTE).forEach { t ->
                                            FilterChip(
                                                selected = typeFilter == t,
                                                onClick = { typeFilter = if (typeFilter == t) null else t },
                                                label = { Text(t.name.lowercase().replaceFirstChar { it.uppercase() }) }
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    val monthFmt = remember { DateTimeFormatter.ofPattern("MMM yyyy") }
                                    val nowMonth = remember { YearMonth.now() }
                                    val anyActive = query.isNotBlank() || typeFilter != null ||
                                        fromMonth != null || toMonth != nowMonth
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        FilterChip(
                                            selected = fromMonth != null,
                                            onClick = { datePickerFor = "from" },
                                            label = { Text(fromMonth?.format(monthFmt) ?: "From") }
                                        )
                                        Text("→", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        FilterChip(
                                            selected = true,
                                            onClick = { datePickerFor = "to" },
                                            label = { Text(toMonth?.format(monthFmt) ?: "To") }
                                        )
                                        if (anyActive) {
                                            IconButton(onClick = {
                                                query = ""; typeFilter = null; fromMonth = null; toMonth = nowMonth
                                            }) {
                                                Icon(Icons.Filled.Close, contentDescription = "Clear filters")
                                            }
                                        }
                                    }
                                    if (datePickerFor != null) {
                                        MonthYearPickerDialog(
                                            initial = (if (datePickerFor == "from") fromMonth else toMonth) ?: nowMonth,
                                            allowAny = datePickerFor == "from",
                                            onPick = { picked ->
                                                if (datePickerFor == "from") fromMonth = picked
                                                else toMonth = picked ?: nowMonth
                                                datePickerFor = null
                                            },
                                            onDismiss = { datePickerFor = null }
                                        )
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                val q = query.trim().lowercase()
                                val filtered = SnipStore.snips.filter { s ->
                                    val ym = YearMonth.from(Instant.ofEpochMilli(s.createdAt).atZone(ZoneId.systemDefault()))
                                    (fromMonth == null || ym >= fromMonth) &&
                                        (toMonth == null || ym <= toMonth) &&
                                        (typeFilter == null || s.type == typeFilter) &&
                                        (q.isEmpty() ||
                                            s.text.lowercase().contains(q) ||
                                            s.source.lowercase().contains(q) ||
                                            s.metadata["title"]?.lowercase()?.contains(q) == true ||
                                            s.metadata["url"]?.lowercase()?.contains(q) == true)
                                }
                                if (filtered.isEmpty()) {
                                    Text(
                                        "No matches",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp
                                    )
                                }
                                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                                    items(filtered, key = { it.id }) { snip ->
                                        val selectionMode = selectedIds.isNotEmpty()
                                        val dismiss = rememberSwipeToDismissBoxState(
                                            positionalThreshold = { it * 0.5f },   // friction: past halfway
                                            confirmValueChange = { v ->
                                                if (v != SwipeToDismissBoxValue.Settled) {   // either direction
                                                    deleteWithUndo(snip); true
                                                } else false
                                            }
                                        )
                                        SwipeToDismissBox(
                                            state = dismiss,
                                            enableDismissFromStartToEnd = !selectionMode,
                                            enableDismissFromEndToStart = !selectionMode,
                                            backgroundContent = {
                                                Row(
                                                    Modifier.fillMaxSize()
                                                        .background(MaterialTheme.colorScheme.errorContainer)
                                                        .padding(horizontal = 24.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.onErrorContainer)
                                                    Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.onErrorContainer)
                                                }
                                            }
                                        ) {
                                            SnipRow(
                                                snip = snip,
                                                selected = snip.id in selectedIds,
                                                selectionMode = selectionMode,
                                                onOpen = {
                                                    // only finished notes open for edit — a
                                                    // recording/transcribing snip isn't ready
                                                    if (snip.status == SnipStatus.DONE || snip.status == SnipStatus.FAILED)
                                                        detailSnip = snip
                                                },
                                                onToggle = {
                                                    if (snip.id in selectedIds) selectedIds.remove(snip.id)
                                                    else selectedIds.add(snip.id)
                                                },
                                                onLongPress = { if (snip.id !in selectedIds) selectedIds.add(snip.id) },
                                                fields = listFields.toSet()
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
                                onDelete = { deleteWithUndo(s); detailSnip = null }
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

                            Text("Audio transcription", fontSize = 16.sp)
                            Text(
                                "Capture audio you're playing and transcribe it on-device. Downloads a ~31 MB model the first time you enable it.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp
                            )
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Enable", Modifier.weight(1f))
                                Switch(
                                    checked = audioEnabled,
                                    enabled = modelPct == null,   // lock while downloading
                                    onCheckedChange = { on ->
                                        audioEnabled = on
                                        settings.audioTranscription = on
                                        if (on && !modelReady) {
                                            modelPct = 0
                                            scope.launch {
                                                val ok = ModelManager.download(this@MainActivity) { p -> modelPct = p }
                                                modelPct = null
                                                if (ok) {
                                                    modelReady = true
                                                    snackbar.showSnackbar("Transcription model ready")
                                                } else {
                                                    audioEnabled = false
                                                    settings.audioTranscription = false
                                                    snackbar.showSnackbar("Model download failed — check connection")
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                            if (audioEnabled) {
                                val pct = modelPct
                                if (pct != null) {
                                    Spacer(Modifier.height(6.dp))
                                    Text("Downloading model… $pct%", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                                    Spacer(Modifier.height(4.dp))
                                    LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth())
                                } else if (modelReady) {
                                    Spacer(Modifier.height(6.dp))
                                    Text("✓ Model ready", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                                    Spacer(Modifier.height(12.dp))
                                    Text("Pre-roll (N): ${pre.toInt()} s")
                                    Slider(
                                        value = pre, onValueChange = { pre = it },
                                        valueRange = 1f..15f, steps = 13   // snap to whole seconds
                                    )
                                    Text("Total clip (K): ${tot.toInt()} s")
                                    Slider(
                                        value = tot,
                                        onValueChange = { tot = it.coerceAtLeast(pre + 1f) },
                                        valueRange = 5f..60f, steps = 54    // snap to whole seconds
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    Text("Watched apps", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                    Text(
                                        "Snips only capture audio from these apps.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
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
                                    Spacer(Modifier.height(8.dp))
                                    TextButton(onClick = {
                                        ModelManager.delete(this@MainActivity)
                                        modelReady = false
                                        audioEnabled = false
                                        settings.audioTranscription = false
                                    }) {
                                        Text(
                                            "Delete model · free 31 MB",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(24.dp))
                            Text("Google Drive sync", fontSize = 16.sp)
                            Text(
                                "Notes sync as plain files to a Drive folder — no account or API. Pick any folder once; Smriti makes its own smriti/ subfolder inside it.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            if (driveConnected) {
                                Text("Connected ✓", color = MaterialTheme.colorScheme.primary)
                                driveFolder?.let { f ->
                                    val path = if (f == "smriti") "smriti/snips/" else "$f/smriti/snips/"
                                    Text(
                                        "📁 $path",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
                                    )
                                }
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
                            Text("List display", fontSize = 16.sp)
                            Text(
                                "Fields shown per note in the list.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Settings.LIST_FIELDS.forEach { (key, label) ->
                                    FilterChip(
                                        selected = key in listFields,
                                        onClick = {
                                            if (key in listFields) listFields.remove(key) else listFields.add(key)
                                            settings.listFields = listFields.toSet()
                                        },
                                        label = { Text(label) }
                                    )
                                }
                            }

                            Spacer(Modifier.height(24.dp))
                            Text("About", fontSize = 16.sp)
                            Text(
                                "Smriti v$appVersion",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { checkForUpdates(false) }) { Text("Check for updates") }
                            Spacer(Modifier.height(24.dp))
                        }

                        if (showPicker) {
                            AppPickerDialog(selected = selected, onDismiss = { showPicker = false })
                        }
                    }
                }
                SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
                if (screen == "main" && audioEnabled && snackbar.currentSnackbarData == null) {
                    val active = running
                    FloatingActionButton(
                        onClick = {
                            if (active) stopSession()
                            else if (selected.isEmpty())
                                scope.launch { snackbar.showSnackbar("Pick an app to watch in Settings first") }
                            else if (!AndroidSettings.canDrawOverlays(this@MainActivity))
                                scope.launch { snackbar.showSnackbar("Grant 'Draw over other apps' in Settings") }
                            else startSession()
                        },
                        containerColor = if (active) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary,
                        contentColor = if (active) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
                    ) {
                        Text(if (active) "■" else "▶", fontSize = 20.sp)
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
    fields: Set<String>,
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
            val glyph = if ("type" in fields) "${typeGlyph(snip.type)} " else ""
            val metaTail = buildList {
                if ("source" in fields && snip.source.isNotBlank()) add(snip.source)
                if ("time" in fields) {
                    val t = DateUtils.getRelativeTimeSpanString(snip.createdAt).toString()
                    add(if (snip.durationS > 0) "$t · ${snip.durationS}s" else t)
                }
            }.joinToString(" · ")
            val metaLine = (glyph + metaTail).trim()
            if (metaLine.isNotEmpty()) {
                Text(metaLine, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if ("title" in fields) snip.metadata["title"]?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if ("url" in fields) snip.metadata["url"]?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.primary
                )
            }
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
    var source by remember(snip.id) { mutableStateOf(snip.source) }
    val meta = remember(snip.id) {
        mutableStateListOf<Pair<String, String>>().apply { addAll(snip.metadata.toList()) }
    }
    var showDetails by remember(snip.id) { mutableStateOf(false) }
    val isSpeech = snip.type == SmaranType.AUDIO || snip.type == SmaranType.VOICE
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "${typeGlyph(snip.type)} ${snip.type.name.lowercase()} · " +
                        DateUtils.getRelativeTimeSpanString(snip.createdAt) +
                        (if (snip.durationS > 0) " · ${snip.durationS}s" else ""),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = edited,
                    onValueChange = { edited = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 280.dp),
                    label = { Text(if (isSpeech) "Transcript" else "Note") }
                )
                Spacer(Modifier.height(8.dp))

                // Metadata — collapsed by default; tap to view/edit source + fields (url, title, …)
                Row(
                    Modifier.fillMaxWidth().clickable { showDetails = !showDetails }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (showDetails) "▾  Details" else "▸  Details", Modifier.weight(1f))
                    if (!showDetails && source.isNotBlank()) {
                        Text(source, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (showDetails) {
                    OutlinedTextField(
                        value = source, onValueChange = { source = it },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("Source") }
                    )
                    Spacer(Modifier.height(6.dp))
                    meta.forEachIndexed { i, entry ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = entry.first, onValueChange = { meta[i] = it to meta[i].second },
                                modifier = Modifier.weight(1f), singleLine = true, label = { Text("key") }
                            )
                            Spacer(Modifier.width(6.dp))
                            OutlinedTextField(
                                value = entry.second, onValueChange = { meta[i] = meta[i].first to it },
                                modifier = Modifier.weight(1.6f), singleLine = true, label = { Text("value") }
                            )
                            IconButton(onClick = { meta.removeAt(i) }) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove field")
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    TextButton(onClick = { meta.add("" to "") }) { Text("＋ Add field") }
                }

                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { shareText(ctx, edited) }) { Text("Share") }
                    Spacer(Modifier.width(4.dp))
                    Button(onClick = {
                        val cleanedMeta = meta.filter { it.first.isNotBlank() }
                            .associate { it.first.trim() to it.second }
                        SnipStore.update(snip.id) {
                            it.copy(text = edited, source = source.trim(), metadata = cleanedMeta)
                        }
                        onDismiss()
                    }) { Text("Save") }
                }
            }
        }
    }
}

@Composable
private fun NewNoteDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("New note", fontSize = 16.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 300.dp),
                    placeholder = { Text("Write a smaran…") }
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(4.dp))
                    Button(onClick = { onSave(text.trim()) }, enabled = text.isNotBlank()) { Text("Save") }
                }
            }
        }
    }
}

private fun typeGlyph(t: SmaranType): String = when (t) {
    SmaranType.AUDIO -> "🎧"
    SmaranType.VOICE -> "🎙"
    SmaranType.WEB -> "🌐"
    SmaranType.TEXT -> "📄"
    SmaranType.NOTE -> "✍"
    SmaranType.UNKNOWN -> "•"
}

/** Calendar-style month/year picker: a year ◀ ▶ stepper + a Jan–Dec grid. Scales to any span.
 *  [allowAny] adds a "clear"/Any option (used by the "From" side). */
@Composable
private fun MonthYearPickerDialog(
    initial: YearMonth,
    allowAny: Boolean,
    onPick: (YearMonth?) -> Unit,
    onDismiss: () -> Unit,
) {
    var year by remember { mutableStateOf(initial.year) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.width(300.dp).padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { year-- }) {
                        Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Previous year")
                    }
                    Text(
                        "$year", Modifier.weight(1f),
                        textAlign = TextAlign.Center, fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = { year++ }) {
                        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "Next year")
                    }
                }
                Spacer(Modifier.height(8.dp))
                for (r in 0 until 4) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (c in 0 until 3) {
                            val m = r * 3 + c + 1
                            val ym = YearMonth.of(year, m)
                            val name = Month.of(m).getDisplayName(TextStyle.SHORT, Locale.getDefault())
                            if (ym == initial) {
                                Button(onClick = { onPick(ym) }, modifier = Modifier.weight(1f)) { Text(name) }
                            } else {
                                OutlinedButton(onClick = { onPick(ym) }, modifier = Modifier.weight(1f)) { Text(name) }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                if (allowAny) {
                    TextButton(onClick = { onPick(null) }, modifier = Modifier.align(Alignment.End)) { Text("Any month") }
                }
            }
        }
    }
}
