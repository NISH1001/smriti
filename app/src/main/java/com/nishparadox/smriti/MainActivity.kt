package com.nishparadox.smriti

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import com.nishparadox.smriti.apps.AppWhitelist
import com.nishparadox.smriti.capture.CaptureService
import com.nishparadox.smriti.settings.Settings
import com.nishparadox.smriti.trigger.FloatingBubbleService
import com.nishparadox.smriti.ui.SmritiTheme
import com.nishparadox.smriti.ui.ThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = Settings(this)
        val mpm = getSystemService(MediaProjectionManager::class.java)

        setContent {
            var themeMode by remember { mutableStateOf(ThemeMode.from(settings.themeMode)) }
            SmritiTheme(themeMode) {
                var screen by remember { mutableStateOf("main") }
                var running by remember { mutableStateOf(CaptureService.instance != null) }
                var pre by remember { mutableStateOf(settings.preRoll.toFloat()) }
                var tot by remember { mutableStateOf(settings.total.toFloat()) }
                var showPicker by remember { mutableStateOf(false) }
                val selected = remember {
                    mutableStateListOf<String>().apply { addAll(settings.whitelist) }
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
                                Modifier.weight(1f).fillMaxWidth(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("स्मृति", fontSize = 68.sp, color = MaterialTheme.colorScheme.primary)
                                Text(
                                    "smriti · capture what you hear",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(40.dp))
                                Text(
                                    if (running) "● Listening — tap the स्म bubble" else "Idle",
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(Modifier.height(16.dp))
                                if (!running) {
                                    Button(
                                        onClick = { startSession() },
                                        enabled = selected.isNotEmpty() && canOverlay,
                                        modifier = Modifier.fillMaxWidth(0.75f).height(54.dp)
                                    ) { Text("▶  Start listening", fontSize = 18.sp) }
                                } else {
                                    Button(
                                        onClick = { stopSession() },
                                        modifier = Modifier.fillMaxWidth(0.75f).height(54.dp)
                                    ) { Text("■  Stop", fontSize = 18.sp) }
                                }
                                if (!canOverlay) {
                                    Spacer(Modifier.height(12.dp))
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
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "Pick an app to watch in Settings",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 13.sp
                                    )
                                }
                            }
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
