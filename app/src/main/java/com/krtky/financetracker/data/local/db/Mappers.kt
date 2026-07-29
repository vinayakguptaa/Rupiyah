package com.krtky.financetracker.data.local.db

import com.krtky.financetracker.domain.model.Category
import com.krtky.financetracker.domain.model.ClassificationStatus
import com.krtky.financetracker.domain.model.Fund
import com.krtky.financetracker.domain.model.Transaction
import com.krtky.financetracker.domain.model.TransactionSource
import com.krtky.financetracker.domain.model.TransactionType
import com.krtky.financetracker.domain.model.TrustedSender

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

fun FundEntity.toDomain() = Fund(
    id = id,
    name = name,
    archived = archived,
    createdAt = createdAt,
    budgetPaise = budgetPaise,
)

fun Fund.toEntity() = FundEntity(
    id = id,
    name = name,
    archived = archived,
    createdAt = createdAt,
    budgetPaise = budgetPaise,
)

fun TrustedSenderEntity.toDomain() = TrustedSender(
    id = id,
    emailPattern = emailPattern,
    walletLabel = walletLabel,
    enabled = enabled,
)

fun TrustedSender.toEntity() = TrustedSenderEntity(
    id = id,
    emailPattern = emailPattern,
    walletLabel = walletLabel,
    enabled = enabled,
)

fun TransactionEntity.toDomain(
    category: Category? = null,
    fundName: String? = null,
) = Transaction(
    id = id,
    type = TransactionType.valueOf(type),
    amountPaise = amountPaise,
    currency = currency,
    occurredAt = occurredAt,
    recordedAt = recordedAt,
    merchant = merchant,
    counterparty = counterparty ?: merchant,
    categoryId = categoryId,
    fundId = fundId,
    paymentMethod = paymentMethod,
    source = TransactionSource.valueOf(source),
    note = note,
    isCash = isCash,
    classificationStatus = ClassificationStatus.valueOf(classificationStatus),
    classificationNotifiedAt = classificationNotifiedAt,
    latitude = latitude,
    longitude = longitude,
    placeName = placeName,
    locationAccuracy = locationAccuracy,
    locationMatchedAt = locationMatchedAt,
    emailMessageId = emailMessageId,
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
    fundName = fundName,
)

fun Transaction.toEntity() = TransactionEntity(
    id = id,
    type = type.name,
    amountPaise = amountPaise,
    currency = currency,
    occurredAt = occurredAt,
    recordedAt = recordedAt,
    merchant = merchant,
    counterparty = counterparty ?: merchant,
    categoryId = categoryId,
    fundId = fundId,
    paymentMethod = paymentMethod,
    source = source.name,
    note = note,
    isCash = isCash,
    classificationStatus = classificationStatus.name,
    classificationNotifiedAt = classificationNotifiedAt,
    latitude = latitude,
    longitude = longitude,
    placeName = placeName,
    locationAccuracy = locationAccuracy,
    locationMatchedAt = locationMatchedAt,
    emailMessageId = emailMessageId,
    externalRefId = externalRefId,
    contentHash = contentHash,
    sheetsSynced = sheetsSynced,
    deletedAt = deletedAt,
    updatedAt = updatedAt,
    version = version,
    receiptUri = receiptUri,
)
