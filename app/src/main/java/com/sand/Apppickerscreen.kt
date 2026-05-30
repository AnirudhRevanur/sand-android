package com.sand

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppPickerScreen() {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    // Trigger recomposition when block state changes
    var refreshTick by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val loaded = getInstalledUserApps(context)
            apps = loaded
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "BLOCK APPS",
            color = Color(0xFFFF4444),
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 4.sp,
            modifier = Modifier.padding(top = 16.dp)
        )

        val blockedCount = remember(refreshTick) { BlockedApps.getBlocked(context).size }
        Text(
            text = "${blockedCount} app${if (blockedCount != 1) "s" else ""} blocked — 30min each — 48h lock",
            color = Color(0xFF666666),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search apps", color = Color(0xFF666666), fontFamily = FontFamily.Monospace) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFFFF4444),
                unfocusedBorderColor = Color(0xFF444444),
                cursorColor = Color(0xFFFF4444)
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFFFF4444))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Loading apps...",
                        color = Color(0xFF666666),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        } else {
            val filtered = if (searchQuery.isBlank()) apps
                          else apps.filter { it.label.contains(searchQuery, ignoreCase = true) }

            // Blocked apps first, then unblocked alphabetically
            val sorted = filtered.sortedWith(
                compareByDescending<AppInfo> { BlockedApps.isBlocked(context, it.packageName) }
                    .thenBy { it.label.lowercase() }
            )

            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(sorted, key = { it.packageName }) { app ->
                    val isBlocked = remember(refreshTick) { BlockedApps.isBlocked(context, app.packageName) }
                    val canUnblock = remember(refreshTick) { BlockedApps.canUnblock(context, app.packageName) }
                    val timeRemaining = remember(refreshTick) { BlockedApps.timeUntilUnblock(context, app.packageName) }

                    AppRow(
                        app = app,
                        isBlocked = isBlocked,
                        canUnblock = canUnblock,
                        timeRemaining = timeRemaining,
                        onBlock = {
                            BlockedApps.block(context, app.packageName)
                            refreshTick++
                        },
                        onUnblock = {
                            BlockedApps.unblock(context, app.packageName)
                            refreshTick++
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AppRow(
    app: AppInfo,
    isBlocked: Boolean,
    canUnblock: Boolean,
    timeRemaining: String,
    onBlock: () -> Unit,
    onUnblock: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppIcon(drawable = app.icon, size = 40)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                color = if (isBlocked) Color(0xFFFF4444) else Color.White,
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (isBlocked) FontWeight.Bold else FontWeight.Normal
            )
            if (isBlocked) {
                Text(
                    text = timeRemaining,
                    color = Color(0xFF666666),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            } else {
                Text(
                    text = app.packageName,
                    color = Color(0xFF444444),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        if (!isBlocked) {
            // Not blocked — show block button
            TextButton(
                onClick = onBlock,
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF4444))
            ) {
                Text(
                    text = "Block",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        } else if (canUnblock) {
            // 48h passed — can unblock
            TextButton(
                onClick = onUnblock,
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF44FF88))
            ) {
                Text(
                    text = "Unblock",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
            }
        } else {
            // Still locked — show lock icon
            Text(
                text = "🔒",
                fontSize = 18.sp,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
}

@Composable
fun AppIcon(drawable: Drawable, size: Int) {
    val bitmap = remember(drawable) {
        drawable.toBitmap(size, size).asImageBitmap()
    }
    Image(
        bitmap = bitmap,
        contentDescription = null,
        modifier = Modifier.size(size.dp)
    )
}
