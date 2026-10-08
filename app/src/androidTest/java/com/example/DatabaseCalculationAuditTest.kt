package com.example

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseCalculationAuditTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun row(body: String, time: Long = 1791453600000L) = Expense(
        amount = 100.10, currency = "INR", merchant = "Cafe", category = "Food", dateInMillis = time,
        originalSms = body, sourceSender = "AX-ICICIB")

    @Test fun distinctPurchasesSurviveAndProvenDuplicatesDoNot() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repo = ExpenseRepository(db.expenseDao())
            assertEquals(2, repo.insertAll(listOf(row("manual-first"), row("manual-second", 1791453601000L))))
            assertEquals(2, repo.insertAll(listOf(row("Paid Rs.100.10 to Cafe UPI Ref 123456"), row("Paid Rs.100.10 to Cafe UPI Ref 123457", 1791453602000L))))
            assertEquals(0, repo.insertAll(listOf(row("Rs.100.10 debited at Cafe UPI Ref 123456", 1791453603000L))))
            assertEquals(1, repo.insertAll(listOf(row("Paid Rs.100.10 to Cafe", 1791453604000L))))
            assertEquals(1, repo.insertAll(listOf(row("Paid Rs.100.10 to Cafe", 1791540004000L))))
            assertEquals(0, repo.insertAll(listOf(row("Paid Rs.100.10 to Cafe", 1791540004000L))))
            assertEquals(6, db.expenseDao().getExpensesList().size)
            assertEquals(600.60, db.expenseDao().getExpensesList().totalAmount(), 0.00001)
            val concurrent = row("Paid Rs.100.10 to Cafe UPI Ref 123458", 1791453608000L)
            val first = async { ExpenseRepository(db.expenseDao()).insertAll(listOf(concurrent)) }
            val second = async { ExpenseRepository(db.expenseDao()).insertAll(listOf(concurrent)) }
            assertEquals(1, first.await() + second.await())
            assertEquals(7, db.expenseDao().getExpensesList().size)
        } finally { db.close() }
    }

    @Test fun deletingOneReceiptDoesNotSuppressTheNextRealPurchase() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repo = ExpenseRepository(db.expenseDao())
            repo.insert(row("Paid Rs.100.10 to Cafe UPI Ref 123456"))
            val saved = db.expenseDao().getExpensesList().single()
            repo.rememberDeleted(saved)
            repo.deleteById(saved.id)
            assertEquals(0, repo.insertAll(listOf(row("Rs.100.10 debited at Cafe UPI Ref 123456", saved.dateInMillis + 120000))))
            assertEquals(1, repo.insertAll(listOf(row("Paid Rs.100.10 to Cafe UPI Ref 123457", saved.dateInMillis + 120000))))
            assertEquals(100.10, db.expenseDao().getExpensesList().totalAmount(), 0.00001)
        } finally { db.close() }
    }

    @Test fun savedParserErrorsAreReconciledWithoutLosingSourceOrProfile() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            db.expenseDao().insertAll(listOf(
                row("Avl bal Rs.9000. Rs.100 debited from your account.").copy(amount = 9000.0, spentBy = "Wife"),
                row("Acct XX070 credited Rs.50 refund for payment of Rs.100 to Cafe.").copy(amount = 50.0),
                row("Payment of Rs.500 to Cafe failed. No money was debited.").copy(amount = 500.0),
                row("manual-kept").copy(amount = 700.0)
            ))
            val repo = ExpenseRepository(db.expenseDao())
            assertEquals(3, repo.reconcileSavedSms())
            assertEquals(0, repo.reconcileSavedSms())
            val rows = db.expenseDao().getExpensesList()
            assertEquals(4, rows.size)
            val debit = rows.single { it.originalSms.startsWith("Avl bal") }
            assertEquals(100.0, debit.amount, 0.00001)
            assertEquals("Wife", debit.spentBy)
            assertEquals("UNCONFIRMED", rows.single { it.originalSms.contains("failed") }.transactionType)
            val summary = buildBudgetSummary(rows, SpendMode.STANDARD, 1000.0, 300.0, 1000.0, 700.0, 1500.0, 1791453600000L)
            assertEquals(800.0, summary.todayTotal, 0.00001)
            assertEquals(50.0, summary.todayCreditTotal, 0.00001)
        } finally { db.close() }
    }

    @Test fun scopedPreferenceTombstonesPreserveLaterIdenticalSms() {
        val isolated = context.createDeviceProtectedStorageContext()
        isolated.getSharedPreferences("spend_radar_prefs", 0).edit().clear().commit()
        try {
            DeletedSmsStore.rememberDeleted(isolated, "Paid Rs.100 to Cafe", 1791453600000)
            assertTrue(DeletedSmsStore.isDeleted(isolated, "Paid Rs.100 to Cafe", 1791453600000))
            assertFalse(DeletedSmsStore.isDeleted(isolated, "Paid Rs.100 to Cafe", 1791540000000))
        } finally { isolated.getSharedPreferences("spend_radar_prefs", 0).edit().clear().commit() }
    }

    @Test fun migrationPreservesVersionSixRowsAndAllowsRepeatedSmsOnDifferentDates() = runBlocking {
        val name = "audit-migration.db"
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name).callback(object : SupportSQLiteOpenHelper.Callback(6) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE expenses (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, amount REAL NOT NULL, currency TEXT NOT NULL, merchant TEXT NOT NULL, category TEXT NOT NULL, dateInMillis INTEGER NOT NULL, originalSms TEXT NOT NULL, spentBy TEXT NOT NULL, transactionType TEXT NOT NULL, sourceSender TEXT NOT NULL)")
                    db.execSQL("CREATE UNIQUE INDEX index_expenses_originalSms ON expenses (originalSms)")
                    db.execSQL("CREATE TABLE deleted_transactions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, merchantKey TEXT NOT NULL, amountCents INTEGER NOT NULL, dayBucket INTEGER NOT NULL, minuteBucket INTEGER NOT NULL, smsFingerprint TEXT NOT NULL, transactionType TEXT NOT NULL, deletedAtMillis INTEGER NOT NULL)")
                    db.execSQL("CREATE UNIQUE INDEX index_deleted_transactions_merchantKey_amountCents_dayBucket_minuteBucket_transactionType ON deleted_transactions (merchantKey, amountCents, dayBucket, minuteBucket, transactionType)")
                    db.execSQL("INSERT INTO expenses VALUES (1, 100.1, 'INR', 'Cafe', 'Food', 1791453600000, 'Paid Rs.100.10 to Cafe', 'Wife', 'DEBIT', 'AX-ICICIB')")
                    db.execSQL("INSERT INTO deleted_transactions VALUES (1, 'cafe', 10010, 1, 1, 'legacy', 'DEBIT', 1)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        helper.writableDatabase
        helper.close()
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(MIGRATION_6_7).build()
        try {
            val old = db.expenseDao().getExpensesList().single()
            assertEquals("Wife", old.spentBy)
            assertEquals(100.10, old.amount, 0.00001)
            assertEquals(1, db.expenseDao().getDeletedTransactions().size)
            assertTrue(db.expenseDao().insertExpense(old.copy(id = 0, dateInMillis = old.dateInMillis + 86400000)) > 0)
            assertEquals(2, db.expenseDao().getExpensesList().size)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
