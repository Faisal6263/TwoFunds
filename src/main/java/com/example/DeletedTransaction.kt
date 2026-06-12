package com.example

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

@Entity(
    tableName = "deleted_transactions",
    indices = [
        Index(
            value = ["merchantKey", "amountCents", "dayBucket", "minuteBucket"],
            unique = true
        )
    ]
)
data class DeletedTransaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val merchantKey: String,
    val amountCents: Long,
    val dayBucket: Long,
    val minuteBucket: Long,
    val smsFingerprint: String,
    val deletedAtMillis: Long
)

fun Expense.toDeletedTransaction(deletedAtMillis: Long = System.currentTimeMillis()): DeletedTransaction =
    DeletedTransaction(
        merchantKey = merchant.transactionKey(),
        amountCents = amount.toAmountCents(),
        dayBucket = dateInMillis.toDayBucket(),
        minuteBucket = dateInMillis.toMinuteBucket(),
        smsFingerprint = originalSms.toSmsFingerprint().orEmpty(),
        deletedAtMillis = deletedAtMillis
    )

fun Expense.isSmsTransaction(): Boolean =
    originalSms.isNotBlank() && !originalSms.startsWith("manual-")

fun List<DeletedTransaction>.matchesDeletedTransaction(expense: Expense): Boolean {
    val merchantKey = expense.merchant.transactionKey()
    val amountCents = expense.amount.toAmountCents()
    val dayBucket = expense.dateInMillis.toDayBucket()
    val minuteBucket = expense.dateInMillis.toMinuteBucket()
    val smsFingerprint = expense.originalSms.toSmsFingerprint()

    return any { deleted ->
        val sameSms = smsFingerprint != null && smsFingerprint == deleted.smsFingerprint
        val sameTransactionIdentity =
            deleted.merchantKey == merchantKey &&
                deleted.amountCents == amountCents &&
                deleted.dayBucket == dayBucket &&
                abs(deleted.minuteBucket - minuteBucket) <= 10

        sameSms || sameTransactionIdentity
    }
}

fun String.toSmsFingerprint(): String? {
    val normalized = lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
    return normalized.takeIf { it.isNotBlank() }
}

private fun String.transactionKey(): String =
    lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")

private fun Double.toAmountCents(): Long = (this * 100).roundToLong()

private fun Long.toMinuteBucket(): Long = this / 60_000L

private fun Long.toDayBucket(): Long {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = this@toDayBucket
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return calendar.timeInMillis
}
