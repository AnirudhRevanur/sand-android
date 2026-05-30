package com.sand

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sand.network.SandClient
import com.sand.tracker.TrackerService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SandApp()
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasUsagePermission() && Settings.canDrawOverlays(this)) {
            startForegroundService(Intent(this, TrackerService::class.java))
        }
    }

    private fun hasUsagePermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
}

@Composable
fun SandApp() {
    var showAppPicker by remember { mutableStateOf(false) }

    if (showAppPicker) {
        Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            // Back button
            TextButton(
                onClick = { showAppPicker = false },
                modifier = Modifier.padding(top = 32.dp, start = 8.dp)
            ) {
                Text(
                    text = "← back",
                    color = Color(0xFFFF4444),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                )
            }
            AppPickerScreen()
        }
    } else {
        SandSettings(onOpenAppPicker = { showAppPicker = true })
    }
}

@Composable
fun SandSettings(onOpenAppPicker: () -> Unit) {
    val context = LocalContext.current
    var ipInput by remember { mutableStateOf(SandClient.getStoredIp(context) ?: "") }
    var savedIp by remember { mutableStateOf(SandClient.getStoredIp(context)) }
    var statusMessage by remember { mutableStateOf("") }

    val hasUsagePermission = remember {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        mutableStateOf(mode == AppOpsManager.MODE_ALLOWED)
    }

    val hasOverlayPermission = remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }

    val hasAccessibility = remember {
        mutableStateOf(isAccessibilityEnabled(context))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "SAND",
                color = Color(0xFFFF4444),
                fontSize = 48.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 12.sp
            )

            Text(
                text = "coarse. rough. irritating.\ngets everywhere.",
                color = Color(0xFF666666),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.Monospace,
                lineHeight = 22.sp
            )

            Divider(color = Color(0xFF333333))

            // ── Permissions ───────────────────────────────────────────────────

            Text(
                text = "PERMISSIONS",
                color = Color(0xFF888888),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 4.sp
            )

            PermissionRow(
                label = "Usage Access",
                granted = hasUsagePermission.value,
                onRequest = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            )

            PermissionRow(
                label = "Draw Overlay",
                granted = hasOverlayPermission.value,
                onRequest = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                }
            )

            PermissionRow(
                label = "Accessibility",
                granted = hasAccessibility.value,
                onRequest = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            )

            Divider(color = Color(0xFF333333))

            // ── Blocked Apps ──────────────────────────────────────────────────

            Text(
                text = "BLOCKED APPS",
                color = Color(0xFF888888),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 4.sp
            )

            val blockedCount = BlockedApps.getBlocked(context).size
            Text(
                text = if (blockedCount == 0) "No apps blocked yet"
                       else "$blockedCount app${if (blockedCount != 1) "s" else ""} blocked",
                color = if (blockedCount == 0) Color(0xFFFF4444) else Color(0xFF44FF88),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )

            Button(
                onClick = onOpenAppPicker,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4444)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Manage Blocked Apps",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Divider(color = Color(0xFF333333))

            // ── API IP ────────────────────────────────────────────────────────

            Text(
                text = "LAPTOP IP",
                color = Color(0xFF888888),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 4.sp
            )

            if (savedIp != null) {
                Text(
                    text = "Current: $savedIp",
                    color = Color(0xFF44FF88),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
            } else {
                Text(
                    text = "No IP set — all blocked apps will be blocked",
                    color = Color(0xFFFF4444),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
            }

            OutlinedTextField(
                value = ipInput,
                onValueChange = { ipInput = it },
                label = {
                    Text(
                        "e.g. 10.125.192.232",
                        color = Color(0xFF666666),
                        fontFamily = FontFamily.Monospace
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFFF4444),
                    unfocusedBorderColor = Color(0xFF444444),
                    cursorColor = Color(0xFFFF4444)
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        SandClient.saveIp(context, ipInput)
                        savedIp = ipInput.substringBefore("/")
                        statusMessage = "Saved."
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4444)),
                    enabled = ipInput.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        SandClient.clearIp(context)
                        savedIp = null
                        ipInput = ""
                        statusMessage = "Cleared."
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF4444)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Clear", fontFamily = FontFamily.Monospace)
                }
            }

            if (statusMessage.isNotBlank()) {
                Text(
                    text = statusMessage,
                    color = Color(0xFF44FF88),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun PermissionRow(label: String, granted: Boolean, onRequest: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace
        )
        if (granted) {
            Text(
                text = "✓ granted",
                color = Color(0xFF44FF88),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )
        } else {
            TextButton(onClick = onRequest) {
                Text(
                    text = "Grant →",
                    color = Color(0xFFFF4444),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

fun isAccessibilityEnabled(context: Context): Boolean {
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabledServices.contains(context.packageName)
}
