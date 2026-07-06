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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nishparadox.smriti.apps.AppWhitelist
import com.nishparadox.smriti.capture.CaptureService
import com.nishparadox.smriti.settings.Settings
import com.nishparadox.smriti.trigger.FloatingBubbleService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = Settings(this)
        val mpm = getSystemService(MediaProjectionManager::class.java)

        setContent {
            MaterialTheme {
                var running by remember { mutableStateOf(CaptureService.instance != null) }
                var pre by remember { mutableStateOf(settings.preRoll.toFloat()) }
                var tot by remember { mutableStateOf(settings.total.toFloat()) }
                val installed = remember { AppWhitelist.installed(this@MainActivity) }
                val selected = remember {
                    mutableStateListOf<String>().apply { addAll(settings.whitelist) }
                }

                val notifPerm = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { }

                val projectionLauncher = rememberLauncherForActivityResult(
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

                Surface {
                    Column(
                        Modifier
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text("Smriti स्म — v0", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(12.dp))

                        Text("Pre-roll (N): ${pre.toInt()} s")
                        Slider(value = pre, onValueChange = { pre = it }, valueRange = 1f..15f)

                        Text("Total clip (K): ${tot.toInt()} s")
                        Slider(
                            value = tot,
                            onValueChange = { tot = it.coerceAtLeast(pre + 1f) },
                            valueRange = 5f..60f
                        )

                        Spacer(Modifier.height(12.dp))
                        Text("Watch these apps:", style = MaterialTheme.typography.titleMedium)
                        if (installed.isEmpty()) {
                            Text("(none of the known media apps are installed)")
                        }
                        installed.forEach { (pkg, label) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = selected.contains(pkg),
                                    onCheckedChange = {
                                        if (it) selected.add(pkg) else selected.remove(pkg)
                                    }
                                )
                                Text(label)
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        val canOverlay = AndroidSettings.canDrawOverlays(this@MainActivity)
                        if (!canOverlay) {
                            Button(onClick = {
                                startActivity(
                                    Intent(
                                        AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:$packageName")
                                    )
                                )
                            }) { Text("1. Grant 'Draw over other apps'") }
                            Spacer(Modifier.height(8.dp))
                        }

                        if (!running) {
                            Button(
                                enabled = selected.isNotEmpty() && canOverlay,
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= 33) {
                                        notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                    projectionLauncher.launch(mpm.createScreenCaptureIntent())
                                }
                            ) { Text("Start listening session") }
                        } else {
                            Button(onClick = {
                                stopService(Intent(this@MainActivity, CaptureService::class.java))
                                stopService(Intent(this@MainActivity, FloatingBubbleService::class.java))
                                running = false
                            }) { Text("Stop session") }
                        }

                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Then open a watched app, play audio, and tap the स्म bubble. " +
                                "The transcript arrives as a notification.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
