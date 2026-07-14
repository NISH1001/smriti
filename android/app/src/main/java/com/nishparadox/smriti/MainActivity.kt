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
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import androidx.documentfile.provider.DocumentFile
import com.nishparadox.smriti.apps.AppWhitelist
import com.nishparadox.smriti.capture.CaptureService
import com.nishparadox.smriti.media.NowPlaying
import com.nishparadox.smriti.notes.DeviceId
import com.nishparadox.smriti.notes.DriveSync
import com.nishparadox.smriti.notes.SmaranType
import com.nishparadox.smriti.notes.Snip
import com.nishparadox.smriti.notes.SnipStatus
import com.nishparadox.smriti.notes.SnipStore
import com.nishparadox.smriti.github.GitHubApi
import com.nishparadox.smriti.github.GitHubConnection
import com.nishparadox.smriti.github.GitHubSource
import com.nishparadox.smriti.search.ExternalHit
import com.nishparadox.smriti.rag.LlamaModelManager
import com.nishparadox.smriti.rag.RagGenerator
import com.nishparadox.smriti.rag.RagModel
import com.nishparadox.smriti.search.SearchIndex
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
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    // Release the RAG model (~1 GB at 1B) when the app is backgrounded or the system reclaims RAM;
    // the .gguf stays on disk, so the next answer just reloads. Process death frees it regardless.
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) RagGenerator.unload()
    }

    /** Bumped on every resume so grants toggled in system Settings (notification access) re-read. */
    private val resumeTick = mutableIntStateOf(0)
    override fun onResume() { super.onResume(); resumeTick.intValue++ }

    @OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = Settings(this)
        val appVersion = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }
            .getOrNull() ?: "?"
        val mpm = getSystemService(MediaProjectionManager::class.java)
        SnipStore.ensureLoaded(this)
        SearchIndex.init(this)
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
                val ghConn = remember { GitHubConnection(this@MainActivity) }
                val ghSource = remember { GitHubSource(ghConn) }
                var ragEnabled by remember { mutableStateOf(settings.ragEnabled) }
                var ragAuto by remember { mutableStateOf(settings.ragAuto) }
                // Batch-undo: rapid swipes collapse into ONE snackbar with a running count, and Undo
                // restores the whole batch — instead of a queue of "Deleted" toasts firing one by one.
                val pendingDeletes = remember { mutableStateListOf<Snip>() }
                var undoJob by remember { mutableStateOf<Job?>(null) }
                fun deleteWithUndo(snip: Snip) {
                    SnipStore.delete(snip.id)
                    pendingDeletes.add(snip)
                    undoJob?.cancel()   // dismiss the current snackbar so the next replaces (not queues) it
                    undoJob = scope.launch {
                        val n = pendingDeletes.size
                        val r = snackbar.showSnackbar(
                            if (n == 1) "Deleted" else "$n deleted",
                            actionLabel = "Undo", duration = SnackbarDuration.Short
                        )
                        if (r == SnackbarResult.ActionPerformed) pendingDeletes.forEach { SnipStore.restore(it) }
                        pendingDeletes.clear()   // only the final (uncancelled) job commits the batch
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
                // Advanced filters (behind the tune icon): free-text substring match, backed by
                // typeahead over the distinct values actually present. Empty = not filtering.
                var titleQuery by remember { mutableStateOf("") }
                var authorQuery by remember { mutableStateOf("") }
                var sourceQuery by remember { mutableStateOf("") }
                var showFilters by remember { mutableStateOf(false) }
                var fromMonth by remember { mutableStateOf<YearMonth?>(null) }
                var toMonth by remember { mutableStateOf<YearMonth?>(YearMonth.now()) }   // "To" defaults to current month
                var externalHits by remember { mutableStateOf<List<ExternalHit>?>(null) }
                var externalLoading by remember { mutableStateOf(false) }
                var externalDetail by remember { mutableStateOf<ExternalHit?>(null) }
                // RAG: answer streams in on search-submit (not per keystroke) over the top-N local hits.
                var answer by remember { mutableStateOf<String?>(null) }
                var answering by remember { mutableStateOf(false) }
                var retrieving by remember { mutableStateOf(false) }
                var answerSources by remember { mutableStateOf<List<Snip>>(emptyList()) }
                var answerExtLabels by remember { mutableStateOf<List<String>>(emptyList()) }
                var genJob by remember { mutableStateOf<Job?>(null) }
                // Editing the query clears/stops any answer (it's for the previous query).
                LaunchedEffect(query) {
                    genJob?.cancel(); answering = false; retrieving = false
                    answer = null; answerSources = emptyList(); answerExtLabels = emptyList()
                }
                fun answerNow() {
                    val q = query.trim()
                    if (q.isBlank() || !ragEnabled) return
                    val model = RagModel.byId(settings.ragModel)
                    if (!LlamaModelManager.isPresent(this@MainActivity, model)) {
                        answerSources = emptyList(); answerExtLabels = emptyList()
                        answer = "Download a model in Settings → RAG to get answers."
                        return
                    }
                    genJob?.cancel()
                    // "retrieving" only when there's external content to fetch over the network;
                    // local notes are already found (they're the on-screen results).
                    // External context is ONLY the Logseq hits already surfaced on-screen (via the
                    // "more smarans in Logseq" action) — never a hidden re-query. Notes-first, then answer.
                    val shownExt = externalHits ?: emptyList()
                    answering = true; retrieving = shownExt.isNotEmpty(); answer = ""; answerSources = emptyList(); answerExtLabels = emptyList()
                    genJob = scope.launch {
                        try {
                            // Context = top-N local smarans + top-N of the external hits already shown.
                            val byId = SnipStore.snips.associateBy { it.id }
                            val localSources = (SearchIndex.search(q) ?: emptyList()).mapNotNull { byId[it] }.take(settings.ragTopN)
                            val extHits = shownExt.take(settings.ragTopN)
                            answerSources = localSources
                            answerExtLabels = extHits.map { it.title }
                            if (localSources.isEmpty() && extHits.isEmpty()) {
                                answer = "No matching notes to answer from."
                                return@launch
                            }
                            // Full note bodies, cleaned by each source's own preprocessor (Logseq
                            // metadata stripped) — a snippet alone is often just the path. The 12k cap
                            // is only a memory guard; the real fit to the model's context window is
                            // done at the token level, natively in startGen.
                            val extParts = extHits.map { hit ->
                                val body = runCatching { ghSource.content(hit) }.getOrNull()?.ifBlank { null }
                                "- ${hit.title}:\n${(body ?: hit.snippet).trim().take(12000)}"
                            }
                            val parts = localSources.map { "- ${it.text.trim().take(12000)}" } + extParts
                            retrieving = false   // notes fully gathered → only now start generating
                            RagGenerator.generate(
                                LlamaModelManager.modelFile(this@MainActivity, model).absolutePath,
                                settings.ragPrompt,
                                "Notes:\n${parts.joinToString("\n\n")}\n\nUsing the notes above, answer this question: $q",
                                nCtx = settings.ragCtx,
                            ).collect { answer = (answer ?: "") + it }
                            if (answer.isNullOrBlank()) answer = "The model couldn't find an answer in these notes."
                        } catch (_: CancellationException) {
                            // stopped by the user
                        } catch (_: Exception) {
                            answer = (answer ?: "") + "\n\n(couldn't generate an answer)"
                        } finally {
                            answering = false; retrieving = false
                        }
                    }
                }
                LaunchedEffect(query) { externalHits = null; externalLoading = false }   // reset on new query
                var datePickerFor by remember { mutableStateOf<String?>(null) }   // "from" | "to" | null
                var audioEnabled by remember { mutableStateOf(settings.audioTranscription) }
                val nowPlayingGranted = remember(resumeTick.intValue) { NowPlaying.hasAccess(this@MainActivity) }
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

                            if (SnipStore.snips.isEmpty() && !ghSource.isEnabled) {
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
                                    val monthFmt = remember { DateTimeFormatter.ofPattern("MMM yyyy") }
                                    val nowMonth = remember { YearMonth.now() }
                                    // "Filters" = everything behind the tune icon — NOT the text query,
                                    // which lives on the front bar. Drives the badge dot.
                                    val filtersActive = typeFilter != null || titleQuery.isNotBlank() ||
                                        authorQuery.isNotBlank() || sourceQuery.isNotBlank() ||
                                        fromMonth != null || toMonth != nowMonth
                                    OutlinedTextField(
                                        value = query,
                                        onValueChange = { query = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text("Search smaran") },
                                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                        trailingIcon = {
                                            BadgedBox(badge = { if (filtersActive) Badge() }) {
                                                IconButton(onClick = { showFilters = true }) {
                                                    Icon(TuneIcon, contentDescription = "Advanced filters")
                                                }
                                            }
                                        },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions = KeyboardActions(onSearch = { if (ragAuto && ragEnabled) answerNow() }),
                                    )
                                    if (showFilters) {
                                        // Typeahead sources: distinct values actually present (recompute on store change).
                                        val titleSuggestions = remember(SnipStore.version) {
                                            SnipStore.snips.mapNotNull(::titleOf).distinct()
                                        }
                                        val authorSuggestions = remember(SnipStore.version) {
                                            SnipStore.snips.mapNotNull { it.metadata["artist"]?.ifBlank { null } }.distinct()
                                        }
                                        val sourceSuggestions = remember(SnipStore.version) {
                                            SnipStore.snips.mapNotNull { it.source.ifBlank { null } }.distinct()
                                        }
                                        ModalBottomSheet(onDismissRequest = { showFilters = false }) {
                                            Column(
                                                Modifier.fillMaxWidth()
                                                    .padding(horizontal = 20.dp).padding(bottom = 24.dp)
                                            ) {
                                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                    Text("Filters", fontSize = 18.sp, modifier = Modifier.weight(1f))
                                                    if (filtersActive) {
                                                        TextButton(onClick = {
                                                            typeFilter = null; titleQuery = ""; authorQuery = ""
                                                            sourceQuery = ""; fromMonth = null; toMonth = nowMonth
                                                        }) { Text("Clear all") }
                                                    }
                                                }
                                                Spacer(Modifier.height(8.dp))
                                                Text("Type", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                                Spacer(Modifier.height(4.dp))
                                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                                Spacer(Modifier.height(16.dp))
                                                FilterField("Title / book", titleQuery, { titleQuery = it }, titleSuggestions)
                                                Spacer(Modifier.height(12.dp))
                                                FilterField("Author", authorQuery, { authorQuery = it }, authorSuggestions)
                                                Spacer(Modifier.height(12.dp))
                                                FilterField("Source / app", sourceQuery, { sourceQuery = it }, sourceSuggestions)
                                                Spacer(Modifier.height(16.dp))
                                                Text("Date", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                                Spacer(Modifier.height(4.dp))
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
                                                        selected = toMonth != nowMonth,
                                                        onClick = { datePickerFor = "to" },
                                                        label = { Text(toMonth?.format(monthFmt) ?: "To") }
                                                    )
                                                }
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
                                // Type + title/author/source + month-range predicate, applied after
                                // text ranking. Title matches book/chapter or album; source matches the
                                // display label (app name / domain). All substring, case-insensitive.
                                val passesFilters: (Snip) -> Boolean = { s ->
                                    val ym = YearMonth.from(Instant.ofEpochMilli(s.createdAt).atZone(ZoneId.systemDefault()))
                                    (fromMonth == null || ym >= fromMonth) &&
                                        (toMonth == null || ym <= toMonth) &&
                                        (typeFilter == null || s.type == typeFilter) &&
                                        (titleQuery.isBlank() || titleOf(s)?.contains(titleQuery, ignoreCase = true) == true) &&
                                        (authorQuery.isBlank() || s.metadata["artist"]?.contains(authorQuery, ignoreCase = true) == true) &&
                                        (sourceQuery.isBlank() || s.source.contains(sourceQuery, ignoreCase = true))
                                }
                                // BM25F ranking via AppSearch (async). null = no searchable query →
                                // keep newest-first. Re-runs on a query change or store change (version).
                                val ranked by produceState<List<Long>?>(null, query, SnipStore.version) {
                                    value = if (query.isBlank()) null else SearchIndex.search(query.trim())
                                }
                                val rankedIds = ranked   // local val: delegated property can't smart-cast
                                val filtered = if (rankedIds == null) {
                                    SnipStore.snips.filter(passesFilters)                 // newest first
                                } else {
                                    val byId = SnipStore.snips.associateBy { it.id }
                                    rankedIds.mapNotNull { byId[it] }.filter(passesFilters)  // best match first
                                }
                                if (filtered.isEmpty() && query.isNotBlank()) {
                                    Text(
                                        "No matches",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp
                                    )
                                }
                                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                                    // ✨ Answer card — above the results, which stay fully intact below.
                                    if (ragEnabled && query.isNotBlank() && (!ragAuto || answering || answer != null)) {
                                        item {
                                            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                                if (answer == null && !answering) {
                                                    OutlinedButton(onClick = { answerNow() }) { Text("✨ Answer from your notes") }
                                                } else {
                                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            when {
                                                                retrieving -> "✨ Answer · reading ${ghSource.label}…"
                                                                answering -> "✨ Answer · generating…"
                                                                else -> "✨ Answer"
                                                            },
                                                            color = MaterialTheme.colorScheme.primary, fontSize = 13.sp,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        if (answering) {
                                                            TextButton(onClick = { genJob?.cancel(); answering = false }) { Text("Stop") }
                                                        }
                                                    }
                                                    Spacer(Modifier.height(4.dp))
                                                    SelectionContainer { Text(answer.orEmpty(), fontSize = 14.sp, lineHeight = 20.sp) }
                                                    val srcLabels = answerSources.map { s ->
                                                        s.metadata["title"]?.takeIf { it.isNotBlank() }
                                                            ?: s.source.takeIf { it.isNotBlank() }
                                                            ?: s.text.take(20)
                                                    } + answerExtLabels
                                                    if (srcLabels.isNotEmpty()) {
                                                        Spacer(Modifier.height(6.dp))
                                                        Text(
                                                            "Sources: " + srcLabels.joinToString(" · "),
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp
                                                        )
                                                    }
                                                }
                                                HorizontalDivider(
                                                    Modifier.padding(top = 10.dp),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
                                                )
                                            }
                                        }
                                    }
                                    items(filtered, key = { it.id }) { snip ->
                                        val selectionMode = selectedIds.isNotEmpty()
                                        // Swipe LEFT → Delete, swipe RIGHT → Share; each reveals a
                                        // button you tap, so a stray scroll-nudge can't delete.
                                        SwipeActionsRow(
                                            enabled = !selectionMode,
                                            canShare = snip.status == SnipStatus.DONE && snip.text.isNotBlank(),
                                            onShare = { shareText(this@MainActivity, snip.text) },
                                            onDelete = { deleteWithUndo(snip) },
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
                                    // External search (opt-in, on-demand): appended BELOW your own
                                    // smarans — different ranking + timing, so never interleaved.
                                    if (query.isNotBlank() && ghSource.isEnabled) {
                                        val hits = externalHits
                                        when {
                                            hits == null -> item {
                                                TextButton(
                                                    enabled = !externalLoading,
                                                    onClick = {
                                                        externalLoading = true
                                                        scope.launch {
                                                            externalHits = ghSource.search(query.trim())
                                                            externalLoading = false
                                                        }
                                                    }
                                                ) {
                                                    Text(
                                                        if (externalLoading) "Searching ${ghSource.label}…"
                                                        else "🌐  more smarans in ${ghSource.label}  →",
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                            hits.isEmpty() -> item {
                                                Text(
                                                    "No matches in ${ghSource.label}",
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontSize = 13.sp,
                                                    modifier = Modifier.padding(12.dp)
                                                )
                                            }
                                            else -> items(hits) { hit ->
                                                ExternalHitRow(hit, onOpen = { externalDetail = hit })
                                            }
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
                        externalDetail?.let { h ->
                            ExternalHitDialog(hit = h, token = ghConn.token, onDismiss = { externalDetail = null })
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

                            Text("Appearance", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
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
                            Text("List display", color = MaterialTheme.colorScheme.onSurfaceVariant)
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

                            Text("Capture", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                            Spacer(Modifier.height(8.dp))
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
                                    Spacer(Modifier.height(16.dp))
                                    Text("Book / track tagging", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                    Text(
                                        "Tag each snip with what was playing (book, chapter, author) so you can search by title. Needs Notification access — Smriti never reads notifications; it's just how Android exposes now-playing info.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    if (nowPlayingGranted) {
                                        Text("Notification access granted ✓", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                                    } else {
                                        OutlinedButton(onClick = {
                                            startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                        }) { Text("Grant notification access") }
                                    }
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
                            Text("Storage & sync", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("Google Drive", fontSize = 16.sp)
                            Text(
                                "Notes sync as plain files to a Drive folder — no account or API. Pick any folder once; Smriti makes its own smriti/ subfolder inside it.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            if (driveConnected) {
                                Text("Connected ✓", color = MaterialTheme.colorScheme.primary)
                                driveFolder?.let { f ->
                                    val path = if (f == "smriti") "smriti/smarans/" else "$f/smriti/smarans/"
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
                            Text("External connectors", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("GitHub", fontSize = 16.sp)
                            Text(
                                "Search your synced Logseq / markdown repos from here. Read-only — nothing is copied into Smriti. Optional, off by default.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            var ghConnected by remember { mutableStateOf(ghConn.isConnected) }
                            var showGhDialog by remember { mutableStateOf(false) }
                            if (ghConnected) {
                                Text("Connected as ${ghConn.account} ✓", color = MaterialTheme.colorScheme.primary)
                                ghConn.repos.forEach { repo ->
                                    Text("• $repo", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                }
                                Spacer(Modifier.height(6.dp))
                                Row {
                                    OutlinedButton(
                                        onClick = { showGhDialog = true }, modifier = Modifier.padding(end = 8.dp)
                                    ) { Text("Edit repos") }
                                    OutlinedButton(onClick = { ghConn.disconnect(); ghConnected = false }) { Text("Disconnect") }
                                }
                            } else {
                                Button(onClick = { showGhDialog = true }) { Text("Connect GitHub") }
                            }
                            if (showGhDialog) {
                                GitHubConnectDialog(
                                    conn = ghConn,
                                    onDone = { ghConnected = ghConn.isConnected; showGhDialog = false },
                                    onDismiss = { showGhDialog = false }
                                )
                            }

                            Spacer(Modifier.height(24.dp))
                            Text("RAG", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("On-device answers", fontSize = 16.sp)
                            Text(
                                "Synthesize an answer from your top matches, fully on-device. Off by default; enabling downloads a small model (~150–250 MB).",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Enable", Modifier.weight(1f))
                                Switch(checked = ragEnabled, onCheckedChange = { ragEnabled = it; settings.ragEnabled = it })
                            }
                            if (ragEnabled) {
                                var ragModelSel by remember { mutableStateOf(settings.ragModel) }
                                val selModel = RagModel.byId(ragModelSel)
                                var present by remember(ragModelSel) { mutableStateOf(LlamaModelManager.isPresent(this@MainActivity, selModel)) }
                                var dlPct by remember(ragModelSel) { mutableStateOf(-1) }
                                Spacer(Modifier.height(8.dp))
                                Text("Model", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    RagModel.entries.forEach { m ->
                                        FilterChip(
                                            selected = ragModelSel == m.id,
                                            onClick = { ragModelSel = m.id; settings.ragModel = m.id },
                                            label = { Text(m.label) }
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                when {
                                    present -> {
                                        Text("✓ ${selModel.label} ready", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                                        TextButton(onClick = { LlamaModelManager.delete(this@MainActivity, selModel); present = false }) {
                                            Text("Delete model · free ${selModel.sizeMb} MB", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                        }
                                    }
                                    dlPct >= 0 -> {
                                        Text("Downloading… $dlPct%", fontSize = 13.sp)
                                        LinearProgressIndicator({ dlPct / 100f }, Modifier.fillMaxWidth().padding(top = 4.dp))
                                    }
                                    else -> Button(onClick = {
                                        dlPct = 0
                                        scope.launch {
                                            val ok = LlamaModelManager.download(this@MainActivity, selModel) { dlPct = it }
                                            present = ok; dlPct = -1
                                        }
                                    }) { Text("Download · ${selModel.sizeMb} MB") }
                                }
                                Spacer(Modifier.height(12.dp))
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Generate automatically")
                                        Text(
                                            "On = answer on each search · Off = tap ✨ Answer",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
                                        )
                                    }
                                    Switch(checked = ragAuto, onCheckedChange = { ragAuto = it; settings.ragAuto = it })
                                }
                                Spacer(Modifier.height(12.dp))
                                var topN by remember { mutableStateOf(settings.ragTopN.toFloat()) }
                                Text("Context: top ${topN.toInt()} smarans")
                                Slider(
                                    value = topN,
                                    onValueChange = { topN = it; settings.ragTopN = it.toInt() },
                                    valueRange = 1f..5f, steps = 3
                                )
                                Spacer(Modifier.height(8.dp))
                                var ctxText by remember { mutableStateOf(settings.ragCtx.toString()) }
                                OutlinedTextField(
                                    value = ctxText,
                                    onValueChange = { v ->
                                        val digits = v.filter { it.isDigit() }.take(5)
                                        ctxText = digits
                                        digits.toIntOrNull()?.let { settings.ragCtx = it }
                                    },
                                    label = { Text("Context window (tokens)") },
                                    supportingText = { Text("Clamped to 1024–8192 and the model's own limit. Higher = richer context, more RAM.") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(8.dp))
                                var prompt by remember { mutableStateOf(settings.ragPrompt) }
                                OutlinedTextField(
                                    value = prompt, onValueChange = { prompt = it; settings.ragPrompt = it },
                                    label = { Text("Instruction") },
                                    modifier = Modifier.fillMaxWidth(), minLines = 2
                                )
                            }

                            Spacer(Modifier.height(24.dp))
                            Text("About", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
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

/**
 * Swipe a row to reveal an action, which you then TAP: swipe LEFT → Delete (red, trailing edge),
 * swipe RIGHT → Share (leading edge, only when [canShare]). Acting needs a deliberate tap, so a
 * stray horizontal nudge during a fast vertical scroll can't delete a note (the old
 * swipe-past-halfway gesture could). A horizontal [draggable] also leaves the LazyColumn's
 * vertical scroll untouched. [enabled] false (selection mode) snaps it closed.
 */
@Composable
private fun SwipeActionsRow(
    enabled: Boolean,
    canShare: Boolean,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    val revealPx = with(LocalDensity.current) { 88.dp.toPx() }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(enabled) { if (!enabled) offset.animateTo(0f) }   // selection mode → snap closed
    // Drag range: left always opens delete; right opens share only when there's something to share.
    val maxOffset = if (canShare) revealPx else 0f

    Box(Modifier.fillMaxWidth()) {
        // Action buttons behind the row: Share on the leading edge, Delete on the trailing edge.
        // Each is hidden under the opaque row until you slide toward it, and tappable only once open.
        Box(Modifier.matchParentSize()) {
            if (canShare) {
                Box(
                    Modifier.align(Alignment.CenterStart).fillMaxHeight().width(88.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(
                        onClick = { scope.launch { offset.animateTo(0f) }; onShare() },
                        enabled = offset.value >= revealPx / 2,
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
            Box(
                Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(88.dp)
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(
                    onClick = { scope.launch { offset.animateTo(0f) }; onDelete() },
                    enabled = offset.value <= -revealPx / 2,
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
        // The row itself, sliding over the buttons. Opaque so only the edge you swipe toward shows.
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .then(
                    if (enabled) Modifier.draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta ->
                            scope.launch { offset.snapTo((offset.value + delta).coerceIn(-revealPx, maxOffset)) }
                        },
                        // Settle open past 40% of the button width, else snap shut.
                        onDragStopped = {
                            scope.launch {
                                offset.animateTo(
                                    when {
                                        offset.value <= -revealPx * 0.4f -> -revealPx
                                        canShare && offset.value >= revealPx * 0.4f -> revealPx
                                        else -> 0f
                                    }
                                )
                            }
                        },
                    ) else Modifier
                )
        ) { content() }
    }
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
        // (Share moved to swipe-right; the note text now spans the full row width.)
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

/** Grouping/display title of a smaran — audio: now-playing book/track (album as fallback);
 *  web: page title. Null when there's nothing meaningful to filter on. */
private fun titleOf(s: Snip): String? =
    s.metadata["title"]?.takeIf { it.isNotBlank() }
        ?: s.metadata["album"]?.takeIf { it.isNotBlank() }

/** The Material "tune" (sliders) glyph, built from its path so we avoid the whole
 *  material-icons-extended artifact (this app ships icons-core only). Icon() tints it. */
private val TuneIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Tune", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).addPath(
        pathData = PathParser().parsePathString(
            "M3,17v2h6v-2H3zM3,5v2h10V5H3zm10,16v-2h8v-2h-8v-2h-2v6h2zM7,9v2H3v2h4v2h2V9H7zm14,4v-2H11v2h10zm-6-4h2V7h4V5h-4V3h-2v6z"
        ).toNodes(),
        fill = SolidColor(Color.Black),
    ).build()
}

/** A free-text filter field with typeahead: as you type, chips of the matching distinct
 *  [suggestions] appear below — tap one to fill it exactly. Substring match is applied by the
 *  caller; picking a suggestion is just a convenience, partial text still filters. */
@Composable
private fun FilterField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    suggestions: List<String>,
) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            singleLine = true,
            trailingIcon = if (value.isNotEmpty()) {
                { IconButton(onClick = { onValue("") }) { Icon(Icons.Filled.Close, contentDescription = "Clear") } }
            } else null,
        )
        val matches = remember(value, suggestions) {
            if (value.isBlank()) emptyList()
            else suggestions.filter { it.contains(value, ignoreCase = true) && !it.equals(value, ignoreCase = true) }.take(6)
        }
        if (matches.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                matches.forEach { m ->
                    SuggestionChip(
                        onClick = { onValue(m) },
                        label = { Text(m.take(30) + if (m.length > 30) "…" else "", maxLines = 1) }
                    )
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

/** A found-not-owned external result: read-only, tap opens the source, source badge + ↗, no swipe. */
@Composable
private fun ExternalHitRow(hit: ExternalHit, onOpen: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 10.dp)
    ) {
        Text(hit.title, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (hit.snippet.isNotBlank() && hit.snippet != hit.title) {
            Text(
                hit.snippet, color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
        Text("🌐 ${hit.sourceLabel} ↗", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
    }
}

/** Connect flow: paste token → verify (GitHub /user) → pick repos → save. */
@Composable
private fun GitHubConnectDialog(conn: GitHubConnection, onDone: () -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf(conn.token ?: "") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var account by remember { mutableStateOf(conn.account) }
    var repos by remember { mutableStateOf<List<GitHubApi.Repo>>(emptyList()) }
    var page by remember { mutableStateOf(1) }
    var hasMore by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>().apply { addAll(conn.repos) } }

    // Edit mode (already connected): pre-fetch the repo list so the checklist shows.
    LaunchedEffect(Unit) {
        if (account != null && token.isNotBlank() && repos.isEmpty()) {
            repos = GitHubApi.listRepos(token.trim(), 1); hasMore = repos.size == 100
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 4.dp) {
            Column(Modifier.padding(20.dp).width(320.dp)) {
                Text("Connect GitHub", fontSize = 18.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Paste a personal access token with read access to the repos you want to search. Stored encrypted on-device; never synced.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = token, onValueChange = { token = it },
                    label = { Text("Token") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
                if (account == null) {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        enabled = token.isNotBlank() && !busy,
                        onClick = {
                            busy = true; error = null
                            scope.launch {
                                val login = GitHubApi.verify(token.trim())
                                if (login == null) { error = "Invalid token, or no access"; busy = false }
                                else {
                                    account = login
                                    repos = GitHubApi.listRepos(token.trim(), 1); hasMore = repos.size == 100
                                    busy = false
                                }
                            }
                        }
                    ) { Text(if (busy) "Verifying…" else "Verify") }
                } else {
                    Spacer(Modifier.height(10.dp))
                    Text("Connected as $account — pick repos to search:", fontSize = 13.sp)
                    var repoFilter by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = repoFilter, onValueChange = { repoFilter = it },
                        label = { Text("Filter (e.g. logseq)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                    )
                    // Your OWN repos first (affiliation=owner → no org/collab noise); "Load more" pages.
                    val shown = repos.filter { it.fullName.contains(repoFilter, ignoreCase = true) }
                    LazyColumn(Modifier.heightIn(max = 260.dp)) {
                        items(shown) { r ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    if (r.fullName in selected) selected.remove(r.fullName) else selected.add(r.fullName)
                                }
                            ) {
                                Checkbox(
                                    checked = r.fullName in selected,
                                    onCheckedChange = {
                                        if (r.fullName in selected) selected.remove(r.fullName) else selected.add(r.fullName)
                                    }
                                )
                                Text(r.fullName + if (r.private) "  🔒" else "", fontSize = 13.sp)
                            }
                        }
                        if (hasMore) {
                            item {
                                TextButton(
                                    enabled = !loadingMore,
                                    onClick = {
                                        loadingMore = true
                                        scope.launch {
                                            val next = GitHubApi.listRepos(token.trim(), page + 1)
                                            repos = repos + next; page += 1
                                            hasMore = next.size == 100; loadingMore = false
                                        }
                                    }
                                ) { Text(if (loadingMore) "Loading…" else "Load more…") }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        enabled = selected.isNotEmpty(),
                        onClick = {
                            conn.token = token.trim(); conn.account = account; conn.repos = selected.toSet(); onDone()
                        }
                    ) { Text("Save (${selected.size})") }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    }
}

/** Read-only in-app view of an external hit's file, fetched token-authed (so private repos work). */
@Composable
private fun ExternalHitDialog(hit: ExternalHit, token: String?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var content by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(hit) {
        content = if (token == null) null else GitHubApi.fetchFile(token, hit.repoFullName, hit.path)
        loading = false
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 4.dp) {
            Column(Modifier.padding(20.dp).width(340.dp)) {
                Text(hit.title, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("🌐 ${hit.sourceLabel} · read-only", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                Box(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                    when {
                        loading -> Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        content == null -> Text("Couldn't load content.", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                        else -> SelectionContainer { Text(content!!, fontSize = 13.sp, lineHeight = 19.sp) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(hit.url))) }
                    }) { Text("Open in browser") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}
