package com.krtky.financetracker.data.local.db

import com.krtky.financetracker.domain.model.Account
import com.krtky.financetracker.domain.model.AccountKind
import com.krtky.financetracker.domain.model.Category
import com.krtky.financetracker.domain.model.ClassificationStatus
import com.krtky.financetracker.domain.model.Tab
import com.krtky.financetracker.domain.model.Transaction
import com.krtky.financetracker.domain.model.TransactionKind
import com.krtky.financetracker.domain.model.TransactionSource
import com.krtky.financetracker.domain.model.TransactionType

fun CategoryEntity.toDomain() = Category(
    id = id,
    name = name,
    icon = icon,
    color = color,
    sortOrder = sortOrder,
    isSystem = isSystem,
    isQuickAction = isQuickAction,
)

fun Category.toEntity() = CategoryEntity(
    id = id,
    name = name,
    icon = icon,
    color = color,
    sortOrder = sortOrder,
    isSystem = isSystem,
    isQuickAction = isQuickAction,
)

fun AccountEntity.toDomain() = Account(
    id = id,
    name = name,
    kind = runCatching { AccountKind.valueOf(kind) }.getOrDefault(AccountKind.BANK),
    currency = currency,
    openingBalancePaise = openingBalancePaise,
    archived = archived,
    sortOrder = sortOrder,
    createdAt = createdAt,
)

fun Account.toEntity() = AccountEntity(
    id = id,
    name = name,
    kind = kind.name,
    currency = currency,
    openingBalancePaise = openingBalancePaise,
    archived = archived,
    sortOrder = sortOrder,
    createdAt = createdAt,
)

fun TabEntity.toDomain() = Tab(
    id = id,
    name = name,
    archived = archived,
    createdAt = createdAt,
    budgetPaise = budgetPaise,
)

fun Tab.toEntity() = TabEntity(
    id = id,
    name = name,
    archived = archived,
    createdAt = createdAt,
    budgetPaise = budgetPaise,
)

/** Map stored type string (DEBIT/CREDIT) to domain. */
fun parseTransactionType(raw: String): TransactionType = when (raw.uppercase()) {
    "DEBIT" -> TransactionType.DEBIT
    "CREDIT" -> TransactionType.CREDIT
    else -> runCatching { TransactionType.valueOf(raw) }.getOrDefault(TransactionType.DEBIT)
}

fun parseTransactionKind(raw: String?): TransactionKind =
    when (raw?.uppercase()) {
        "SELF_TRANSFER" -> TransactionKind.SELF_TRANSFER
        "TAB_TRANSFER" -> TransactionKind.TAB_TRANSFER
        else -> TransactionKind.NORMAL
    }

fun TransactionEntity.toDomain(
    category: Category? = null,
    tabName: String? = null,
    accountName: String? = null,
) = Transaction(
    id = id,
    type = parseTransactionType(type),
    amountPaise = amountPaise,
    currency = currency,
    occurredAt = occurredAt,
    recordedAt = recordedAt,
    counterparty = counterparty,
    categoryId = categoryId,
    tabId = tabId,
    accountId = accountId,
    source = runCatching { TransactionSource.valueOf(source) }.getOrDefault(TransactionSource.MANUAL),
    note = note,
    isCash = isCash,
    classificationStatus = runCatching { ClassificationStatus.valueOf(classificationStatus) }
        .getOrDefault(ClassificationStatus.PENDING),
    isSkipped = isSkipped || classificationStatus == ClassificationStatus.SKIPPED.name,
    kind = parseTransactionKind(kind),
    transferGroupId = transferGroupId,
    rawDescription = rawDescription,
    classificationNotifiedAt = classificationNotifiedAt,
    latitude = latitude,
    longitude = longitude,
    placeName = placeName,
    locationAccuracy = locationAccuracy,
    locationMatchedAt = locationMatchedAt,
    smsMessageId = smsMessageId,
    externalRefId = externalRefId,
    contentHash = contentHash,
    sheetsSynced = sheetsSynced,
    deletedAt = deletedAt,
    updatedAt = updatedAt,
    version = version,
    receiptUri = receiptUri,
    categoryName = category?.name,
    categoryIcon = category?.icon,
    categoryColor = category?.color,
    tabName = tabName,
    accountName = accountName,
    splitGroupId = splitGroupId,
)

fun Transaction.toEntity() = TransactionEntity(
    id = id,
    type = type.name,
    amountPaise = amountPaise,
    currency = currency,
    occurredAt = occurredAt,
    recordedAt = recordedAt,
    counterparty = counterparty,
    categoryId = categoryId,
    tabId = tabId,
    accountId = accountId,
    source = source.name,
    note = note,
    isCash = isCash,
    classificationStatus = when {
        isSkipped -> ClassificationStatus.SKIPPED.name
        categoryId != null -> ClassificationStatus.CLASSIFIED.name
        else -> classificationStatus.name
    },
    isSkipped = isSkipped || classificationStatus == ClassificationStatus.SKIPPED,
    kind = kind.name,
    transferGroupId = transferGroupId,
    rawDescription = rawDescription,
    classificationNotifiedAt = classificationNotifiedAt,
    latitude = latitude,
    longitude = longitude,
    placeName = placeName,
    locationAccuracy = locationAccuracy,
    locationMatchedAt = locationMatchedAt,
    smsMessageId = smsMessageId,
    externalRefId = externalRefId,
    contentHash = contentHash,
    sheetsSynced = sheetsSynced,
    deletedAt = deletedAt,
    updatedAt = updatedAt,
    version = version,
    receiptUri = receiptUri,
    splitGroupId = splitGroupId,
)
