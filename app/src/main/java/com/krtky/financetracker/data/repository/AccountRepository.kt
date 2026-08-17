package com.krtky.financetracker.data.repository

import com.krtky.financetracker.data.local.db.AccountEntity
import com.krtky.financetracker.data.local.db.AppDatabase
import com.krtky.financetracker.data.local.db.toDomain
import com.krtky.financetracker.data.local.db.toEntity
import com.krtky.financetracker.domain.model.Account
import com.krtky.financetracker.domain.model.AccountBalance
import com.krtky.financetracker.domain.model.AccountKind
import com.krtky.financetracker.domain.model.TransactionKind
import com.krtky.financetracker.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    db: AppDatabase,
) {
    private val accountDao = db.accountDao()
    private val txnDao = db.transactionDao()

    /** Active accounts only — Add Transaction, Self Transfer, defaults. */
    fun observeActive(): Flow<List<Account>> =
        accountDao.observeActive().map { list -> list.map { it.toDomain() } }

    /** Archived banks/cards/wallets (Cash is never archived). History kept. */
    fun observeArchived(): Flow<List<Account>> =
        accountDao.observeArchived().map { list -> list.map { it.toDomain() } }

    /** Active first, then archived — Settings bank list UI. */
    fun observeAll(): Flow<List<Account>> =
        accountDao.observeAll().map { list -> list.map { it.toDomain() } }

    /** Active balances only (main Accounts screen). */
    fun observeBalances(): Flow<List<AccountBalance>> =
        balancesFlow(activeOnly = true)

    /** Active + archived balances (Settings manage list). */
    fun observeAllBalances(): Flow<List<AccountBalance>> =
        balancesFlow(activeOnly = false)

    /** Digital rows with no owning account (display-only bucket). */
    fun observeUnassignedDigital(): Flow<UnassignedDigital> =
        txnDao.observeAll().map { txns ->
            val mine = txns.filter {
                it.deletedAt == null &&
                    it.accountId == null &&
                    !it.isCash
            }
            val net = mine.sumOf { t ->
                if (t.type.equals("CREDIT", true)) t.amountPaise else -t.amountPaise
            }
            UnassignedDigital(count = mine.size, netPaise = net)
        }

    private fun balancesFlow(activeOnly: Boolean): Flow<List<AccountBalance>> {
        val accountsFlow = if (activeOnly) accountDao.observeActive() else accountDao.observeAll()
        return combine(accountsFlow, txnDao.observeAll()) { accounts, txns ->
            accounts.map { entity ->
                val account = entity.toDomain()
                val mine = txns.filter {
                    it.deletedAt == null && it.accountId == account.id
                }
                val net = mine.sumOf { t ->
                    val type = t.type.uppercase()
                    val signed = when {
                        type == "CREDIT" -> t.amountPaise
                        else -> -t.amountPaise
                    }
                    signed
                }
                AccountBalance(
                    account = account,
                    balancePaise = account.openingBalancePaise + net,
                    txnCount = mine.size.toLong(),
                )
            }
        }
    }

    suspend fun getAll(): List<Account> = accountDao.getAll().map { it.toDomain() }

    suspend fun getById(id: Long): Account? = accountDao.getById(id)?.toDomain()

    suspend fun getByName(name: String): Account? = accountDao.getByName(name)?.toDomain()

    suspend fun upsert(account: Account): Long = accountDao.upsert(account.toEntity())

    suspend fun archive(id: Long) {
        val existing = accountDao.getById(id) ?: return
        if (existing.name.equals("Cash", true)) return
        if (!existing.archived) {
            accountDao.update(existing.copy(archived = true))
        }
    }

    suspend fun unarchive(id: Long) {
        val existing = accountDao.getById(id) ?: return
        if (existing.archived) {
            accountDao.update(existing.copy(archived = false))
        }
    }

    /**
     * Add a bank/card/wallet by name, or restore if it was archived.
     * Does not touch Cash.
     * @return account id, or null if name blank / is Cash.
     */
    suspend fun addOrRestore(name: String, kind: AccountKind? = null): Long? {
        val trimmed = name.trim()
        if (trimmed.isBlank() || trimmed.equals("Cash", true)) return null
        val existing = accountDao.getByName(trimmed)
        return if (existing == null) {
            accountDao.upsert(
                AccountEntity(
                    name = trimmed,
                    kind = (kind ?: inferKind(trimmed)).name,
                    sortOrder = 50,
                    archived = false,
                ),
            )
        } else {
            accountDao.update(
                existing.copy(
                    archived = false,
                    kind = if (existing.kind.isBlank()) {
                        (kind ?: inferKind(trimmed)).name
                    } else {
                        existing.kind
                    },
                ),
            )
            existing.id
        }
    }

    /**
     * Align accounts table with Settings → active bank names.
     * - Cash is always present and active
     * - Names in [bankNames] are added / un-archived
     * - When [bankNames] is **non-empty**, other non-Cash accounts are **archived**
     *   (txns kept; hidden from Add pickers)
     * - When [bankNames] is **empty**, only Cash is ensured — existing accounts are left alone
     *   so migration-seeded banks are not wiped on first launch with empty prefs
     */
    suspend fun syncFromBankList(bankNames: List<String>) {
        val names = bankNames
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals("Cash", true) }
            .distinctBy { it.lowercase() }
        val allowedLower = names.map { it.lowercase() }.toSet()

        val cash = accountDao.getByName("Cash")
        if (cash == null) {
            accountDao.upsert(
                AccountEntity(name = "Cash", kind = AccountKind.CASH.name, sortOrder = 0),
            )
        } else if (cash.archived) {
            accountDao.update(cash.copy(archived = false, sortOrder = 0))
        }

        if (names.isEmpty()) {
            // Empty prefs: do not archive migration-seeded or user accounts.
            return
        }

        var order = 1
        for (name in names) {
            val existing = accountDao.getByName(name)
            if (existing == null) {
                accountDao.upsert(
                    AccountEntity(
                        name = name,
                        kind = inferKind(name).name,
                        sortOrder = order++,
                    ),
                )
            } else {
                accountDao.update(
                    existing.copy(
                        archived = false,
                        sortOrder = order++,
                        kind = if (existing.kind.isBlank()) inferKind(name).name else existing.kind,
                    ),
                )
            }
        }

        for (acc in accountDao.getAll()) {
            if (acc.name.equals("Cash", true)) continue
            if (acc.name.lowercase() !in allowedLower && !acc.archived) {
                accountDao.update(acc.copy(archived = true))
            }
        }
    }

    /** Active non-Cash names — for prefs backup / SMS match list. */
    suspend fun activeBankNames(): List<String> =
        accountDao.getAll()
            .filter { !it.archived && !it.name.equals("Cash", true) }
            .sortedBy { it.sortOrder }
            .map { it.name }

    /**
     * Resolve account id from free-text payment label.
     * Matches existing accounts only (including archived) so history stays linked.
     * Creates Cash if missing. Does **not** invent new bank rows from free text.
     */
    suspend fun resolveId(methodLabel: String?, isCash: Boolean): Long? {
        if (isCash || methodLabel.equals("Cash", true)) {
            return accountDao.getByName("Cash")?.id
                ?: accountDao.upsert(
                    AccountEntity(name = "Cash", kind = AccountKind.CASH.name, sortOrder = 0),
                )
        }
        val label = methodLabel?.trim().orEmpty()
        if (label.isBlank() || label.equals("Digital", true) || label.equals("UPI", true)) {
            return null
        }
        return accountDao.getByName(label)?.id
    }

    private fun inferKind(name: String): AccountKind = when {
        name.equals("Cash", true) -> AccountKind.CASH
        name.contains("card", true) -> AccountKind.CARD
        name.contains("wallet", true) ||
            name.contains("PhonePe", true) ||
            name.equals("GPay", true) ||
            name.equals("Paytm", true) ||
            name.contains("Pay", true) && name.length <= 12 -> AccountKind.WALLET
        else -> AccountKind.BANK
    }

    companion object {
        fun isDebitType(type: String): Boolean =
            type.uppercase() == TransactionType.DEBIT.name

        fun isCreditType(type: String): Boolean =
            type.uppercase() == TransactionType.CREDIT.name

        fun isSelfTransferKind(kind: String?): Boolean =
            kind?.uppercase() == TransactionKind.SELF_TRANSFER.name
    }
}

data class UnassignedDigital(
    val count: Int,
    val netPaise: Long,
)
