package com.sand.overlay

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sand.MainActivity
import com.sand.network.SandClient
import com.sand.network.VerifyRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OverlayActivity : ComponentActivity() {

    companion object {
        const val EXTRA_MINUTES = "extra_minutes"
        const val EXTRA_LEVEL = "extra_level"
        const val EXTRA_NO_API = "extra_no_api"
        const val EXTRA_APP_ID = "extra_app_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* no escape */ }
        })

        val minutes = intent.getIntExtra(EXTRA_MINUTES, 0)
        val level = intent.getIntExtra(EXTRA_LEVEL, 1)
        val noApi = intent.getBooleanExtra(EXTRA_NO_API, false)
        val appId = intent.getStringExtra(EXTRA_APP_ID) ?: "unknown"

        setContent {
            SandOverlay(
                minutesUsed = minutes,
                level = level,
                noApi = noApi,
                appId = appId,
                onAccessGranted = { finish() }
            )
        }
    }
}

@Composable
fun SandOverlay(
    minutesUsed: Int,
    level: Int,
    noApi: Boolean,
    appId: String,
    onAccessGranted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentProblem by remember { mutableStateOf("") }
    var currentToken by remember { mutableStateOf("") }
    var setId by remember { mutableStateOf("") }
    var remainingCount by remember { mutableStateOf(0) }
    var totalCount by remember { mutableStateOf(0) }
    var userAnswer by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var problemsFetched by remember { mutableStateOf(false) }

    val shameMessage = when {
        minutesUsed >= 90 -> "${minutesUsed} minutes. You need help."
        minutesUsed >= 60 -> "${minutesUsed} minutes. Embarrassing."
        minutesUsed >= 30 -> "You've used ${minutesUsed} minutes today."
        else -> "Laptop not reachable. No access for you."
    }

    val levelLabel = when (level) {
        1 -> "Level 1 — Multiplication"
        2 -> "Level 2 — Algebra"
        3 -> "Level 3 — Derivatives"
        4 -> "Level 4 — Integration"
        5 -> "Level 5 — Linear Algebra"
        else -> "No API. No access."
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = "SAND",
                color = Color(0xFFFF4444),
                fontSize = 48.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 12.sp
            )

            TextButton(
                onClick = {
                    context.startActivity(
                        Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                    )
                }
            ) {
                Text(
                    text = "⚙ settings",
                    color = Color(0xFF555555),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
            }

            Text(
                text = shameMessage,
                color = Color(0xFFAAAAAA),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.Monospace
            )

            Divider(color = Color(0xFF333333), thickness = 1.dp)

            if (noApi) {
                Text(
                    text = "Laptop unreachable.\nOpen your laptop first.\nStart the API.\nThen try again.",
                    color = Color(0xFFFF4444),
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 32.sp
                )
            } else {
                Text(
                    text = levelLabel,
                    color = Color(0xFF888888),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )

                if (!problemsFetched) {
                    Button(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                statusMessage = ""
                                try {
                                    val api = SandClient.buildFromPrefs(context)
                                    if (api == null) {
                                        statusMessage = "No API configured."
                                        isLoading = false
                                        return@launch
                                    }
                                    val response = api.generate(appId)
                                    if (response.isSuccessful) {
                                        val body = response.body()!!
                                        if (!body.problems.isNullOrEmpty()) {
                                            setId = body.set_id
                                            currentToken = body.problems.first().token
                                            currentProblem = body.problems.first().problem
                                            totalCount = body.problems.size
                                            remainingCount = body.problems.size
                                            problemsFetched = true
                                        } else {
                                            statusMessage = "No problems returned."
                                        }
                                    } else {
                                        statusMessage = "API error: ${response.code()}"
                                    }
                                } catch (e: Exception) {
                                    statusMessage = "Can't reach laptop."
                                }
                                isLoading = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4444)),
                        enabled = !isLoading
                    ) {
                        Text(
                            text = if (isLoading) "Loading..." else "Get Problems",
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    Text(
                        text = "Problem ${totalCount - remainingCount + 1} of $totalCount",
                        color = Color(0xFF666666),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Text(
                        text = currentProblem,
                        color = Color.White,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 28.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    OutlinedTextField(
                        value = userAnswer,
                        onValueChange = { userAnswer = it },
                        label = {
                            Text(
                                "Answer",
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
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                scope.launch {
                                    submitAnswer(
                                        context, appId, userAnswer, setId, currentToken,
                                        onCorrectMore = { nt, np, r ->
                                            currentToken = nt
                                            currentProblem = np
                                            remainingCount = r
                                            userAnswer = ""
                                            statusMessage = "Correct. Keep going."
                                        },
                                        onGranted = onAccessGranted,
                                        onWrong = { msg ->
                                            statusMessage = msg
                                            problemsFetched = false
                                            userAnswer = ""
                                        }
                                    )
                                }
                            }
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                submitAnswer(
                                    context, appId, userAnswer, setId, currentToken,
                                    onCorrectMore = { nt, np, r ->
                                        currentToken = nt
                                        currentProblem = np
                                        remainingCount = r
                                        userAnswer = ""
                                        statusMessage = "Correct. Keep going."
                                    },
                                    onGranted = onAccessGranted,
                                    onWrong = { msg ->
                                        statusMessage = msg
                                        problemsFetched = false
                                        userAnswer = ""
                                    }
                                )
                                isLoading = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4444)),
                        enabled = !isLoading && userAnswer.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isLoading) "Checking..." else "Submit",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (statusMessage.isNotBlank()) {
                    Text(
                        text = statusMessage,
                        color = if (statusMessage.startsWith("Correct")) Color(0xFF44FF88) else Color(0xFFFF4444),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

private suspend fun submitAnswer(
    context: android.content.Context,
    appId: String,
    userAnswer: String,
    setId: String,
    token: String,
    onCorrectMore: (nextToken: String, nextProblem: String, remaining: Int) -> Unit,
    onGranted: () -> Unit,
    onWrong: (String) -> Unit
) = withContext(Dispatchers.IO) {
    try {
        val api = SandClient.buildFromPrefs(context)
            ?: return@withContext withContext(Dispatchers.Main) { onWrong("No API. Blocked.") }

        val response = api.verify(VerifyRequest(appId, setId, token, userAnswer))
        if (response.isSuccessful) {
            val body = response.body()!!
            withContext(Dispatchers.Main) {
                when {
                    body.correct && body.remaining == 0 -> onGranted()
                    body.correct && body.remaining > 0 -> onCorrectMore(
                        body.next_token!!,
                        body.next_problem!!,
                        body.remaining
                    )
                    else -> onWrong(body.message)
                }
            }
        } else {
            withContext(Dispatchers.Main) { onWrong("API error: ${response.code()}") }
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) { onWrong("Can't reach laptop.") }
    }
}
