package com.example

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY dateInMillis DESC")
    fun getAllExpenses(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses")
    suspend fun getExpensesList(): List<Expense>

    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    suspend fun getExpenseById(id: Int): Expense?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(expenses: List<Expense>)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpenseById(id: Int)

    @Query("SELECT * FROM deleted_transactions")
    suspend fun getDeletedTransactions(): List<DeletedTransaction>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDeletedTransaction(deletedTransaction: DeletedTransaction)
}
