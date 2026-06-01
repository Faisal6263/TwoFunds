package com.example

import kotlinx.coroutines.flow.Flow

class ExpenseRepository(private val expenseDao: ExpenseDao) {
    val allExpenses: Flow<List<Expense>> = expenseDao.getAllExpenses()

    private fun isDuplicate(newExpense: Expense, existing: List<Expense>): Boolean {
        val cleanNewSms = if (newExpense.originalSms.isBlank()) "" else newExpense.originalSms.lowercase().replace(Regex("[^a-z0-9]"), "")
        
        for (old in existing) {
            // 1. If SMS content is identical, it's a duplicate
            if (cleanNewSms.isNotEmpty() && old.originalSms.isNotBlank()) {
                val cleanOldSms = old.originalSms.lowercase().replace(Regex("[^a-z0-9]"), "")
                if (cleanNewSms == cleanOldSms) return true
            }
            
            // 2. Check similar transaction window (within 5 minutes, same amount and merchant)
            val timeDiff = Math.abs(newExpense.dateInMillis - old.dateInMillis)
            if (timeDiff < 300000 && 
                newExpense.amount == old.amount && 
                newExpense.merchant.equals(old.merchant, ignoreCase = true)
            ) {
                return true
            }
        }
        return false
    }

    suspend fun insert(expense: Expense) {
        val existing = expenseDao.getExpensesList()
        val cleanNewSms = if (expense.originalSms.isBlank()) "" else expense.originalSms.lowercase().replace(Regex("[^a-z0-9]"), "")
        val hasDuplicate = existing.any { old ->
            if (cleanNewSms.isNotEmpty() && old.originalSms.isNotBlank()) {
                val cleanOldSms = old.originalSms.lowercase().replace(Regex("[^a-z0-9]"), "")
                if (cleanNewSms == cleanOldSms) return@any true
            }
            val timeDiff = Math.abs(expense.dateInMillis - old.dateInMillis)
            timeDiff < 300000 && expense.amount == old.amount && expense.merchant.equals(old.merchant, ignoreCase = true)
        }
        if (!hasDuplicate) {
            expenseDao.insertExpense(expense)
        }
    }
    
    suspend fun insertAll(expenses: List<Expense>) {
        val existing = expenseDao.getExpensesList()
        val existingSmsSet = existing.mapNotNull { 
            val clean = it.originalSms.lowercase().replace(Regex("[^a-z0-9]"), "")
            if (clean.isEmpty()) null else clean 
        }.toMutableSet()
        
        // Group existing by amount to avoid iterating the whole database for window checks
        val existingByAmount = existing.groupBy { it.amount }.mapValues { entry ->
            entry.value.toMutableList()
        }.toMutableMap()
        
        val nonDuplicates = mutableListOf<Expense>()
        
        for (expense in expenses) {
            val cleanNewSms = if (expense.originalSms.isBlank()) "" else expense.originalSms.lowercase().replace(Regex("[^a-z0-9]"), "")
            
            var isDup = false
            if (cleanNewSms.isNotEmpty() && existingSmsSet.contains(cleanNewSms)) {
                isDup = true
            } else {
                // Check similar transaction window (within 5 minutes, same amount and merchant) from existing list by amount
                val sameAmountList = existingByAmount[expense.amount]
                if (sameAmountList != null) {
                    for (old in sameAmountList) {
                        val timeDiff = Math.abs(expense.dateInMillis - old.dateInMillis)
                        if (timeDiff < 300000 && expense.merchant.equals(old.merchant, ignoreCase = true)) {
                            isDup = true
                            break
                        }
                    }
                }
            }
            
            if (!isDup) {
                nonDuplicates.add(expense)
                if (cleanNewSms.isNotEmpty()) {
                    existingSmsSet.add(cleanNewSms)
                }
                existingByAmount.getOrPut(expense.amount) { mutableListOf() }.add(expense)
            }
        }
        if (nonDuplicates.isNotEmpty()) {
            expenseDao.insertAll(nonDuplicates)
        }
    }

    suspend fun deleteById(id: Int) = expenseDao.deleteExpenseById(id)
}
