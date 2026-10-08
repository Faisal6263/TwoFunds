package com.example

import kotlinx.coroutines.flow.Flow
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val ingestionMutex = Mutex()

fun Expense.referenceIdentity(): String? {
    if (!isSmsTransaction()) return null
    val reference = Regex("(?i)\\b(?:UPI\\s+(?:ref(?:erence)?|txn)|UTR|RRN|(?:txn|transaction)\\s*(?:id|ref(?:erence)?))\\s*(?:no\\.?|number)?\\s*[:#-]?\\s*([A-Za-z0-9]{6,})\\b")
        .find(originalSms)?.groupValues?.get(1)?.uppercase() ?: return null
    val bank = listOf("ICICI", "HDFC", "SBI", "AXIS", "KOTAK").firstOrNull {
        sourceSender.contains(it, true)
    } ?: sourceSender.uppercase().replace(Regex("^[A-Z]{2}-"), "").takeIf { it.isNotBlank() } ?: return null
    return "$bank|${currency.uppercase()}|$transactionType|$reference"
}

class ExpenseRepository(private val expenseDao: ExpenseDao) {
    val allExpenses: Flow<List<Expense>> = expenseDao.getAllExpenses()

    // Amount, merchant and proximity alone do not identify a transaction.
    private fun Expense.smsIdentity(): Pair<String, Long>? =
        if (isSmsTransaction()) originalSms.trim().replace(Regex("\\s+"), " ") to dateInMillis else null

    suspend fun insert(expense: Expense) {
        insertAll(listOf(expense))
    }

    suspend fun insertAll(expenses: List<Expense>): Int = ingestionMutex.withLock {
        val deletedTransactions = expenseDao.getDeletedTransactions()
        val existing = expenseDao.getExpensesList()
        val existingByIdentity = existing.mapNotNull { row -> row.smsIdentity()?.let { it to row } }
            .toMap().toMutableMap()
        val existingManualIds = existing.filter { !it.isSmsTransaction() }.map { it.originalSms }.toMutableSet()
        val existingReferences = existing.mapNotNull { row -> row.referenceIdentity()?.let { it to row.amount } }.toMap().toMutableMap()
        val newRows = mutableListOf<Expense>()
        for (input in expenses) {
            if (!input.amount.isFinite() || input.amount < 0 || input.dateInMillis <= 0 || (!input.isDebit && !input.isCredit)) continue
            val expense = input.copy(amount = roundMoney(input.amount), originalSms = input.originalSms.ifBlank { "manual-${UUID.randomUUID()}" })
            if (expense.isSmsTransaction() && deletedTransactions.matchesDeletedTransaction(expense)) continue
            val reference = expense.referenceIdentity()
            if (reference != null && existingReferences[reference] == expense.amount) continue
            val identity = expense.smsIdentity()
            val old = identity?.let(existingByIdentity::get)
            if (old != null) {
                if (old.id != 0 && old.sourceSender.isBlank() && expense.sourceSender.isNotBlank()) {
                    val updated = old.copy(sourceSender = expense.sourceSender)
                    expenseDao.updateExpense(updated)
                    existingByIdentity[identity] = updated
                }
                continue
            }
            if (identity == null && !existingManualIds.add(expense.originalSms)) continue
            newRows.add(expense)
            if (identity != null) existingByIdentity[identity] = expense
            if (reference != null) existingReferences[reference] = expense.amount
        }
        if (newRows.isEmpty()) return@withLock 0
        // IGNORE plus the database identity constraint also protects concurrent SMS ingestion.
        expenseDao.insertAll(newRows).count { it != -1L }
    }

    suspend fun getById(id: Int): Expense? = expenseDao.getExpenseById(id)

    /** Reconcile older parser results to their saved source without deleting evidence. */
    suspend fun reconcileSavedSms(): Int = ingestionMutex.withLock {
        var corrected = 0
        for (row in expenseDao.getExpensesList().filter { it.isSmsTransaction() }) {
            val parsed = parseExpenseFromSms(row.sourceSender, row.originalSms, row.dateInMillis)
            val updated = when {
                isUnsettledFinancialSms(row.originalSms) -> row.copy(transactionType = "UNCONFIRMED")
                parsed != null -> row.copy(amount = parsed.amount, currency = parsed.currency, transactionType = parsed.transactionType)
                else -> row // Unsupported or ambiguous messages need source reconciliation.
            }
            if (updated != row) {
                expenseDao.updateExpense(updated)
                corrected++
            }
        }
        corrected
    }

    suspend fun rememberDeleted(expense: Expense) {
        expenseDao.insertDeletedTransaction(expense.toDeletedTransaction())
    }

    suspend fun removeDeletedSmsBackedExpenses(isDeletedSms: (Expense) -> Boolean): Int {
        val deleted = expenseDao.getDeletedTransactions()
        val rows = expenseDao.getExpensesList().filter {
            it.isSmsTransaction() && (isDeletedSms(it) || deleted.matchesDeletedTransaction(it))
        }
        rows.forEach { expenseDao.deleteExpenseById(it.id) }
        return rows.size
    }

    suspend fun deleteById(id: Int) = expenseDao.deleteExpenseById(id)
}
