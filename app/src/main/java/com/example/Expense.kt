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
    val transactionType: String = TransactionType.DEBIT.name
)

val Expense.isCredit: Boolean get() = transactionType == TransactionType.CREDIT.name
val Expense.isDebit: Boolean get() = !isCredit

const val TRACKED_CREDIT_UPI_ID = "Faisal62632@ibl"

fun String.containsTrackedCreditUpi(): Boolean =
    contains(TRACKED_CREDIT_UPI_ID, ignoreCase = true)

val Expense.isTrackedCredit: Boolean
    get() = isCredit && originalSms.containsTrackedCreditUpi()

enum class TransactionType {
    DEBIT,
    CREDIT
}

enum class SpenderProfile(val displayName: String, val emoji: String) {
    HUSBAND("Husband", "👨"),
    WIFE("Wife", "👩"),
    SHARED("Shared", "🤝")
}
