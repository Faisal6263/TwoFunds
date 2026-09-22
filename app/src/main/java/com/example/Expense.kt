package com.example

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "expenses",
    indices = [Index(value = ["originalSms"], unique = true)]
)
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val amount: Double,
    val currency: String,
    val merchant: String,
    val category: String,
    val dateInMillis: Long,
    val originalSms: String,
    val spentBy: String = SpenderProfile.HUSBAND.displayName,
    val transactionType: String = TransactionType.DEBIT.name,
    val sourceSender: String = ""
)

val Expense.isCredit: Boolean get() = transactionType == TransactionType.CREDIT.name
val Expense.isDebit: Boolean get() = !isCredit

const val TRACKED_CREDIT_UPI_ID = "Faisal62632@ibl"
const val TRACKED_CREDIT_BANK = "ICICI"
const val TRACKED_CREDIT_ACCOUNT = "XX070"

fun String.containsTrackedCreditUpi(): Boolean =
    contains(TRACKED_CREDIT_UPI_ID, ignoreCase = true)

fun String.containsTrackedCreditAccount(): Boolean =
    Regex("(?i)\\b(?:acct|a/c|account)\\s*(?:no\\.?\\s*)?${Regex.escape(TRACKED_CREDIT_ACCOUNT)}\\b")
        .containsMatchIn(this)

fun isTrackedCreditDestination(sender: String, body: String): Boolean =
    (sender.contains(TRACKED_CREDIT_BANK, ignoreCase = true) ||
        body.contains(TRACKED_CREDIT_BANK, ignoreCase = true)) &&
        (body.containsTrackedCreditUpi() || body.containsTrackedCreditAccount())

val Expense.isTrackedCredit: Boolean
    get() = isCredit && isTrackedCreditDestination(sourceSender, originalSms)

enum class TransactionType {
    DEBIT,
    CREDIT
}

enum class SpenderProfile(val displayName: String, val emoji: String) {
    HUSBAND("Husband", "👨"),
    WIFE("Wife", "👩"),
    SHARED("Shared", "🤝")
}
