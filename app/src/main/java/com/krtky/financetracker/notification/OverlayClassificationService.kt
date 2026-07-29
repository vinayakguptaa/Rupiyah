package com.krtky.financetracker.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.krtky.financetracker.R
import com.krtky.financetracker.data.repository.CategoryRepository
import com.krtky.financetracker.data.repository.TransactionRepository
import com.krtky.financetracker.domain.model.Category
import com.krtky.financetracker.domain.model.FundBalance
import com.krtky.financetracker.domain.model.Transaction
import com.krtky.financetracker.domain.model.TransactionType
import com.krtky.financetracker.ui.theme.RobotoFlex
import com.krtky.financetracker.ui.theme.RupiyahTheme
import com.krtky.financetracker.ui.util.inr
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class OverlayClassificationService : Service() {
    @Inject lateinit var transactionRepository: TransactionRepository
    @Inject lateinit var categoryRepository: CategoryRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var windowManager: WindowManager? = null
    private var overlay: ComposeView? = null
    private var transactionId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        transactionId = intent?.getStringExtra(OverlayClassificationReceiver.EXTRA_TXN_ID)
        val id = transactionId
        if (id == null || !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        scope.launch {
            val txn = transactionRepository.getById(id)
            val categories = categoryRepository.observeAll().first()
            val funds = transactionRepository.observeFunds().first()
            launchOverlay(id, txn, categories, funds)
        }
        return START_NOT_STICKY
    }

    private fun launchOverlay(
        id: String,
        txn: Transaction?,
        categories: List<Category>,
        funds: List<FundBalance>,
    ) {
        val view = ComposeView(this).apply {
            setContent {
                RupiyahTheme(
                    darkTheme = true,
                    themeMode = com.krtky.financetracker.ui.theme.ThemeMode.MATERIAL_YOU,
                ) {
                    OverlayClassifierContent(
                        txn = txn,
                        categories = categories,
                        funds = funds,
                        onConfirm = { categoryId, fundId, note ->
                            scope.launch {
                                transactionRepository.classify(id, categoryId, note.ifBlank { null }, fundId)
                                NotificationManagerCompat.from(this@OverlayClassificationService)
                                    .cancel(10_000 + (id.hashCode() and 0xFFFF))
                                stopSelf()
                            }
                        },
                        onDismiss = { stopSelf() },
                    )
                }
            }
        }
        overlay = view
        windowManager = getSystemService(WindowManager::class.java)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM
            dimAmount = 0.45f
            flags = flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
        }
        try {
            windowManager?.addView(view, params)
        } catch (_: Exception) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        try {
            overlay?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {
        }
        overlay = null
        windowManager = null
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Classifier overlay", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun buildNotification(): Notification {
        val pending = PendingIntent.getActivity(
            this,
            0,
            Intent(this, com.krtky.financetracker.ui.MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Classifying payment")
            .setContentText("Tap to return to Rupiyah")
            .setContentIntent(pending)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "classifier_overlay"
        private const val NOTIF_ID = 73
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverlayClassifierContent(
    txn: Transaction?,
    categories: List<Category>,
    funds: List<FundBalance>,
    onConfirm: (Long?, Long?, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val shapes = MaterialTheme.shapes
    val scheme = MaterialTheme.colorScheme
    var categoryId by remember { mutableStateOf<Long?>(txn?.categoryId) }
    var fundId by remember { mutableStateOf<Long?>(txn?.fundId) }
    var note by remember { mutableStateOf(txn?.note.orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    val isExpense = txn?.type != TransactionType.INCOME
    val amountColor = if (isExpense) scheme.error else scheme.primary

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 10.dp),
        color = scheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Classify payment",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = RobotoFlex,
            )
            txn?.let {
                val sign = if (isExpense) "−" else "+"
                val party = it.counterparty ?: it.merchant ?: "Payment"
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = shapes.extraLarge,
                    color = scheme.surfaceContainerLowest,
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "$sign${it.amountPaise.inr()}",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Black,
                            fontFamily = RobotoFlex,
                            color = amountColor,
                        )
                        Text(
                            party,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Text(
                "Category",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = categoryId == null,
                    onClick = { categoryId = null },
                    label = { Text("None") },
                    shape = shapes.large,
                )
                categories.forEach { category ->
                    FilterChip(
                        selected = categoryId == category.id,
                        onClick = { categoryId = category.id },
                        label = { Text(category.name) },
                        shape = shapes.large,
                    )
                }
            }
            Text(
                "Fund",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = fundId == null,
                    onClick = { fundId = null },
                    label = { Text("None") },
                    shape = shapes.large,
                )
                funds.forEach { fund ->
                    FilterChip(
                        selected = fundId == fund.fund.id,
                        onClick = { fundId = fund.fund.id },
                        label = { Text(fund.fund.name) },
                        shape = shapes.large,
                    )
                }
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                shape = shapes.large,
            )
            Spacer(Modifier.height(2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text("Later") }
                Button(
                    onClick = {
                        if (!saving) {
                            saving = true
                            onConfirm(categoryId, fundId, note)
                        }
                    },
                    modifier = Modifier.weight(1.4f).height(52.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = scheme.primary),
                ) {
                    Text("Confirm", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
