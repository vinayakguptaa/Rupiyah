package com.krtky.financetracker.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.krtky.financetracker.data.repository.CategoryRepository
import com.krtky.financetracker.data.repository.TransactionRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ClassificationActionReceiver : BroadcastReceiver() {

    @Inject lateinit var transactionRepository: TransactionRepository
    @Inject lateinit var categoryRepository: CategoryRepository
    @Inject lateinit var notifier: ClassificationNotifier

    override fun onReceive(context: Context, intent: Intent) {
        val txnId = intent.getStringExtra(EXTRA_TXN_ID) ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_CATEGORY -> {
                        val catId = intent.getLongExtra(EXTRA_CATEGORY_ID, -1L).takeIf { it > 0 }
                        transactionRepository.classify(txnId, catId, null, null)
                        NotificationManagerCompat.from(context).cancel(txnId.hashCode())
                    }
                    ACTION_FUND -> {
                        val tabId = intent.getLongExtra(EXTRA_TAB_ID, -1L).takeIf { it > 0 }
                        transactionRepository.classify(txnId, null, null, tabId)
                        // Keep notification so user can still add a note
                        notifier.notifyPayment(txnId)
                    }
                    ACTION_REPLY -> {
                        val reply = RemoteInput.getResultsFromIntent(intent)
                            ?.getCharSequence(KEY_REPLY)
                            ?.toString()
                            ?.trim()
                        if (!reply.isNullOrBlank()) {
                            val cats = categoryRepository.getAll()
                            val match = cats.firstOrNull { it.name.equals(reply, true) }
                            transactionRepository.classify(
                                txnId,
                                match?.id,
                                if (match == null) reply else null,
                                null,
                            )
                            val txn = transactionRepository.getById(txnId)
                            if (txn?.categoryId == null) {
                                notifier.notifyPayment(txnId)
                            } else {
                                NotificationManagerCompat.from(context).cancel(txnId.hashCode())
                            }
                        }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_CATEGORY = "com.krtky.financetracker.ACTION_CATEGORY"
        const val ACTION_FUND = "com.krtky.financetracker.ACTION_FUND"
        const val ACTION_REPLY = "com.krtky.financetracker.ACTION_REPLY"
        const val EXTRA_TXN_ID = "txn_id"
        const val EXTRA_CATEGORY_ID = "category_id"
        const val EXTRA_TAB_ID = "fund_id"
        const val KEY_REPLY = "key_reply"
    }
}
