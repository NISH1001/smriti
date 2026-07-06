package com.nishparadox.smriti

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                        Column(Modifier.fillMaxSize().padding(20.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { screen = "settings" }) { Text("⚙  Settings") }
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
                        val apps = remember { AppWhitelist.installedLaunchable(this@MainActivity) }
                        LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
                            item {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = {
                                        settings.preRoll = pre.toInt()
                                        settings.total = tot.toInt()
                                        settings.whitelist = selected.toSet()
                                        screen = "main"
                                    }) { Text("‹  Back") }
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
                                Spacer(Modifier.height(16.dp))
                                Text("Watch these apps (${selected.size} selected):", fontSize = 16.sp)
                                Text(
                                    "Snips only capture audio from the apps you check here.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            items(apps) { (pkg, label) ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = selected.contains(pkg),
                                        onCheckedChange = {
                                            if (it) selected.add(pkg) else selected.remove(pkg)
                                        }
                                    )
                                    Text(label)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
