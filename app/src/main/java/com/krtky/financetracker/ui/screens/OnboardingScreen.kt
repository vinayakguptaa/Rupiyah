package com.krtky.financetracker.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krtky.financetracker.R
import com.krtky.financetracker.ui.components.GroupedCard
import com.krtky.financetracker.ui.theme.M3EMotion
import com.krtky.financetracker.ui.viewmodel.OnboardingViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    vm: OnboardingViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val shapes = MaterialTheme.shapes
    val context = LocalContext.current

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = vm.importData(context, uri)
            val msg = result.fold({ it }, { it.message ?: "Import failed" })
            vm.setStatus(msg)
            if (result.isSuccess) vm.setBackupImported()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        vm.setNotificationGranted(granted)
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        vm.setLocationGranted(
            result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        )
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) vm.setSmsEnabled(true)
    }

    // Dynamic page list: after backup import, skip credential pages
    val pages: List<Int> = remember(state.backupImported) {
        if (state.backupImported) {
            // Only permission pages after import
            listOf(0, 3, 4, 7)
        } else {
            // Full flow
            listOf(0, 1, 2, 3, 4, 5, 6, 7)
        }
    }
    val totalPages = pages.size

    val pagerState = rememberPagerState(pageCount = { totalPages })

    fun currentPageId(): Int = pages[pagerState.currentPage]

    fun nextPage() {
        scope.launch {
            val next = pagerState.currentPage + 1
            if (next < totalPages) pagerState.animateScrollToPage(next)
            else {
                vm.completeOnboarding()
                onDone()
            }
        }
    }

    fun skipToEnd() {
        vm.completeOnboarding()
        onDone()
    }

    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            // Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                userScrollEnabled = false,
            ) { idx ->
                when (pages[idx]) {
                    0 -> WelcomePage(onNext = ::nextPage)
                    1 -> ImportBackupPage(
                        onImport = { importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                        onNext = ::nextPage,
                        imported = state.backupImported,
                        status = state.status,
                    )
                    2 -> GmailPage(
                        gmail = state.gmail,
                        onGmailChange = vm::setGmail,
                        gmailPassSet = state.gmailPassSet,
                        onSaveGmail = vm::saveGmail,
                    )
                    3 -> LocationPage(
                        granted = state.locationGranted,
                        onRequest = {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                )
                            )
                        },
                    )
                    4 -> NotificationPage(
                        granted = state.notificationGranted,
                        onRequest = {
                            if (Build.VERSION.SDK_INT >= 33) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                vm.setNotificationGranted(true)
                            }
                        },
                    )
                    5 -> SmsPage(
                        senders = state.smsSenders,
                        keywords = state.smsKeywords,
                        onSendersChange = vm::setSmsSenders,
                        onKeywordsChange = vm::setSmsKeywords,
                        onSave = { vm.saveSmsRules(state.smsSenders, state.smsKeywords) },
                        smsEnabled = state.smsEnabled,
                        onRequestPermission = {
                            smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
                        },
                    )
                    6 -> LlmPage(
                        baseUrl = state.llmBaseUrl,
                        model = state.llmModel,
                        apiKeySet = state.llmApiKeySet,
                        onBaseUrlChange = vm::setLlmBaseUrl,
                        onModelChange = vm::setLlmModel,
                        onSave = vm::saveLlm,
                    )
                    7 -> DonePage(
                        imported = state.backupImported,
                        onNext = { vm.completeOnboarding(); onDone() },
                    )
                }
            }

            // Bottom controls — sit above 3-button / gesture nav
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Page dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 16.dp),
                ) {
                    repeat(totalPages) { i ->
                        Box(
                            Modifier
                                .size(if (i == pagerState.currentPage) 10.dp else 8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i == pagerState.currentPage) scheme.primary
                                    else scheme.outlineVariant,
                                ),
                        )
                    }
                }

                // Progress bar
                LinearWavyProgressIndicator(
                    progress = { (pagerState.currentPage + 1f) / totalPages },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(WavyProgressIndicatorDefaults.LinearContainerHeight),
                    color = scheme.primary,
                    trackColor = scheme.surfaceContainerHighest,
                )
                Spacer(Modifier.height(16.dp))

                // Buttons row
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (pagerState.currentPage > 0) {
                        OutlinedButton(
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                            },
                            modifier = Modifier.weight(1f),
                            shape = shapes.large,
                        ) { Text("Back") }
                    }

                    if (pagerState.currentPage == 0) {
                        OutlinedButton(
                            onClick = ::skipToEnd,
                            modifier = Modifier.weight(1f),
                            shape = shapes.large,
                        ) { Text("Skip all") }
                    }

                    val isLast = pagerState.currentPage == totalPages - 1
                    Button(
                        onClick = ::nextPage,
                        modifier = Modifier.weight(if (pagerState.currentPage == 0) 1.5f else 1f),
                        shape = shapes.large,
                    ) {
                        Text(
                            if (isLast) "Get started" else "Next",
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            if (isLast) Icons.Default.Check
                            else Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

// ── Page 1: Welcome ─────────────────────────────────────────────────────────

@Composable
private fun WelcomePage(onNext: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(100.dp)
                .background(scheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.WavingHand,
                contentDescription = null,
                tint = scheme.onPrimaryContainer,
                modifier = Modifier.size(52.dp),
            )
        }
        Spacer(Modifier.height(32.dp))
        Text(
            "Welcome to Rupiyah",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Your personal finance tracker.\nLog expenses, track envelopes, and watch your spending — all in one place.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = scheme.onSurfaceVariant,
            lineHeight = 24.sp,
        )
        Spacer(Modifier.height(40.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
        ) { Text("Get started", style = MaterialTheme.typography.labelLarge) }
    }
}

// ── Page 2: Import Backup ───────────────────────────────────────────────────

@Composable
private fun ImportBackupPage(onImport: () -> Unit, onNext: () -> Unit, imported: Boolean = false, status: String? = null) {
    val scheme = MaterialTheme.colorScheme
    val shapes = MaterialTheme.shapes
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(96.dp)
                .background(if (imported) scheme.primaryContainer else scheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (imported) Icons.Default.Check else Icons.Default.Backup,
                contentDescription = null,
                tint = if (imported) scheme.onPrimaryContainer else scheme.onSecondaryContainer,
                modifier = Modifier.size(48.dp),
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(
            if (imported) "Backup restored!" else "Restore from backup",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            if (imported) {
                "Your transactions, categories, and settings have been restored. You can still configure permissions on the next pages."
            } else {
                "Have a Rupiyah backup file? Import it now to restore your transactions, categories, and settings."
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = scheme.onSurfaceVariant,
            lineHeight = 22.sp,
        )
        Spacer(Modifier.height(32.dp))
        if (!imported) {
            FilledTonalButton(
                onClick = onImport,
                modifier = Modifier.fillMaxWidth(),
                shape = shapes.large,
            ) { Text("Import backup file") }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onNext) { Text("Skip — start fresh") }
        } else {
            if (status != null) {
                Text(status, color = scheme.primary, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
            }
            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth(),
                shape = shapes.large,
            ) { Text("Continue") }
        }
    }
}

// ── Page 3: Gmail ───────────────────────────────────────────────────────────

@Composable
private fun GmailPage(
    gmail: String,
    onGmailChange: (String) -> Unit,
    gmailPassSet: Boolean,
    onSaveGmail: (String, String?) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shapes = MaterialTheme.shapes
    var password by remember { mutableStateOf("") }
    var showHelp by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Box(
            Modifier
                .size(80.dp)
                .background(scheme.tertiaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Email,
                contentDescription = null,
                tint = scheme.onTertiaryContainer,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Gmail settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Why Gmail? Pull bank alerts into Activity automatically.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.onboarding_optional_later),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            color = scheme.primary,
        )
        Spacer(Modifier.height(24.dp))

        GroupedCard(padded = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Gmail IMAP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = { showHelp = true }) {
                    Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Help")
                }
            }
            OutlinedTextField(
                gmail,
                { onGmailChange(it) },
                label = { Text("Gmail address") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = shapes.medium,
            )
            OutlinedTextField(
                password,
                { password = it },
                label = { Text(if (gmailPassSet) "App password (saved)" else "App password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = shapes.medium,
            )
            Button(
                onClick = {
                    onSaveGmail(gmail, password.ifBlank { null })
                    password = ""
                },
                modifier = Modifier.fillMaxWidth(),
                shape = shapes.large,
            ) { Text("Save Gmail") }
            if (gmailPassSet) {
                Text("Gmail configured", color = scheme.primary, style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(16.dp))
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("Gmail App Password") },
            text = {
                Text(
                    buildString {
                        append("Google does NOT allow your normal Gmail password for IMAP.\n\n")
                        append("Steps:\n")
                        append("1. Open Google Account → Security\n")
                        append("2. Turn on 2-Step Verification (required)\n")
                        append("3. Open App passwords\n")
                        append("4. Select app: Mail, device: Other → type Rupiyah\n")
                        append("5. Tap Generate\n")
                        append("6. Copy the 16-character password\n")
                        append("7. Paste it above\n\n")
                        append("Tip: Spaces are OK — the app strips them.")
                    },
                )
            },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text("Got it") } },
        )
    }
}

// ── Page 4: Location ────────────────────────────────────────────────────────

@Composable
private fun LocationPage(granted: Boolean, onRequest: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(80.dp)
                .background(scheme.errorContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                tint = scheme.onErrorContainer,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Location",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Optional — attach your current location when logging a transaction.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Location is sampled only when you log a transaction — never tracked continuously.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))

        if (granted) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(28.dp)
                        .background(scheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Check, null, tint = scheme.onPrimary, modifier = Modifier.size(16.dp)) }
                Spacer(Modifier.width(8.dp))
                Text("Location granted", color = scheme.primary, style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            FilledTonalButton(
                onClick = onRequest,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            ) { Text("Allow location access") }
        }
    }
}

// ── Page 5: Notifications ───────────────────────────────────────────────────

@Composable
private fun NotificationPage(granted: Boolean, onRequest: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(80.dp)
                .background(scheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Notifications,
                contentDescription = null,
                tint = scheme.onPrimaryContainer,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Notifications",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Rupiyah uses notifications to classify incoming transactions and alert you about new emails.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))

        if (granted) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(28.dp)
                        .background(scheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Check, null, tint = scheme.onPrimary, modifier = Modifier.size(16.dp)) }
                Spacer(Modifier.width(8.dp))
                Text("Notifications granted", color = scheme.primary, style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            FilledTonalButton(
                onClick = onRequest,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            ) { Text("Allow notifications") }
        }
    }
}

// ── Page 6: SMS ─────────────────────────────────────────────────────────────

@Composable
private fun SmsPage(
    senders: String,
    keywords: String,
    onSendersChange: (String) -> Unit,
    onKeywordsChange: (String) -> Unit,
    onSave: () -> Unit,
    smsEnabled: Boolean,
    onRequestPermission: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shapes = MaterialTheme.shapes

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Box(
            Modifier
                .size(80.dp)
                .background(scheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Sms,
                contentDescription = null,
                tint = scheme.onPrimaryContainer,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "SMS settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Optional — Rupiyah can parse bank transaction SMS messages to auto-log expenses.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        GroupedCard(padded = true) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Enable SMS monitoring", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = smsEnabled,
                    onCheckedChange = {
                        if (!smsEnabled) onRequestPermission()
                    },
                )
            }
            OutlinedTextField(
                senders,
                { onSendersChange(it) },
                label = { Text("Allowed senders") },
                supportingText = { Text("Comma-separated sender IDs, e.g. HDFCBK, AX-ICICIB") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                shape = shapes.medium,
            )
            OutlinedTextField(
                keywords,
                { onKeywordsChange(it) },
                label = { Text("Transaction keywords") },
                supportingText = { Text("Comma-separated. A matching keyword allows inspection regardless of sender.") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                shape = shapes.medium,
            )
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
                shape = shapes.large,
            ) { Text("Save SMS rules") }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ── Page 7: LLM Provider ───────────────────────────────────────────────────

@Composable
private fun LlmPage(
    baseUrl: String,
    model: String,
    apiKeySet: Boolean,
    onBaseUrlChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onSave: (String, String, String?) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shapes = MaterialTheme.shapes
    var apiKey by remember { mutableStateOf("") }
    var showHelp by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Box(
            Modifier
                .size(80.dp)
                .background(scheme.tertiaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Psychology,
                contentDescription = null,
                tint = scheme.onTertiaryContainer,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "AI provider",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Optional — connect an OpenAI-compatible API (Groq, OpenAI, Ollama) to auto-classify transactions.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.onboarding_optional_later),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            color = scheme.primary,
        )
        Spacer(Modifier.height(24.dp))

        GroupedCard(padded = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Provider", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = { showHelp = true }) {
                    Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Help")
                }
            }
            OutlinedTextField(
                baseUrl,
                { onBaseUrlChange(it) },
                label = { Text("Base URL") },
                modifier = Modifier.fillMaxWidth(),
                shape = shapes.medium,
            )
            OutlinedTextField(
                model,
                { onModelChange(it) },
                label = { Text("Model") },
                modifier = Modifier.fillMaxWidth(),
                shape = shapes.medium,
            )
            OutlinedTextField(
                apiKey,
                { apiKey = it },
                label = { Text(if (apiKeySet) "API key (saved — enter to replace)" else "API key") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = shapes.medium,
            )
            Button(
                onClick = { onSave(baseUrl, model, apiKey.ifBlank { null }); apiKey = "" },
                modifier = Modifier.fillMaxWidth(),
                shape = shapes.large,
            ) { Text("Save provider") }

            Text("Presets", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = false,
                    onClick = {
                        onBaseUrlChange("https://api.groq.com/openai/v1")
                        onModelChange("llama-3.3-70b-versatile")
                    },
                    label = { Text("Groq") },
                    shape = shapes.medium,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = scheme.secondaryContainer,
                    ),
                )
                FilterChip(
                    selected = false,
                    onClick = {
                        onBaseUrlChange("https://api.openai.com/v1")
                        onModelChange("gpt-4o-mini")
                    },
                    label = { Text("OpenAI") },
                    shape = shapes.medium,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = scheme.secondaryContainer,
                    ),
                )
            }
            if (apiKeySet) {
                Text("API key saved", color = scheme.primary, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("LLM API Key") },
            text = {
                Text(
                    buildString {
                        append("Option A — Groq (free tier)\n")
                        append("1. Sign up at console.groq.com\n")
                        append("2. Create an API key\n")
                        append("3. Base URL: https://api.groq.com/openai/v1\n")
                        append("4. Model: llama-3.3-70b-versatile\n\n")
                        append("Option B — OpenAI\n")
                        append("1. Sign up at platform.openai.com\n")
                        append("2. Add billing if required\n")
                        append("3. Create an API key\n")
                        append("4. Base URL: https://api.openai.com/v1\n")
                        append("5. Model: gpt-4o-mini\n\n")
                        append("Option C — Any OpenAI-compatible host\n")
                        append("Base URL must end before /chat/completions.")
                    },
                )
            },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text("Got it") } },
        )
    }
}

// ── Page: Done ──────────────────────────────────────────────────────────────

@Composable
private fun DonePage(imported: Boolean, onNext: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(100.dp)
                .background(scheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = scheme.onPrimaryContainer,
                modifier = Modifier.size(52.dp),
            )
        }
        Spacer(Modifier.height(32.dp))
        Text(
            "You\u2019re all set!",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            if (imported) {
                "Your data is restored and permissions are ready. Tap below to start using Rupiyah."
            } else {
                "Rupiyah is ready. You can configure Gmail, SMS, and AI later in Settings."
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = scheme.onSurfaceVariant,
            lineHeight = 24.sp,
        )
        Spacer(Modifier.height(40.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
        ) { Text("Get started", style = MaterialTheme.typography.labelLarge) }
    }
}
