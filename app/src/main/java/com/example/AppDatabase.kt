package com.example

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [Expense::class, DeletedTransaction::class], version = 7, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
}
