package com.example

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.Calendar

/** Runs the real activity, preferences, Room flows, navigation and financial screens. */
@RunWith(AndroidJUnit4::class)
class ApplicationCalculationAuditTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val db get() = DefaultDatabase.getInstance(context)
    private var scenario: ActivityScenario<MainActivity>? = null
    private val now get() = System.currentTimeMillis()

    @Before fun reset() {
        db.clearAllTables()
        context.getSharedPreferences("spend_radar_prefs", 0).edit().clear()
            .putString("daily_pacing_limit", "300.00").putString("monthly_budget", "1000.00")
            .putString("weekly_budget", "700.00").putString("weekend_allowance", "1500.00").commit()
    }

    @After fun close() { scenario?.close() }

    private fun seed() = runBlocking {
        val today = now - 3600000
        val yesterday = Calendar.getInstance().apply { timeInMillis = today; add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
        fun e(amount: Double, merchant: String, profile: String, type: String = "DEBIT", body: String = "manual-$merchant") =
            Expense(amount = amount, currency = "INR", merchant = merchant, category = if (merchant == "Metro") "Transport" else "Food",
                dateInMillis = today, originalSms = body, spentBy = profile, transactionType = type)
        db.expenseDao().insertAll(listOf(
            e(450.10, "Cafe Today", "Husband"), e(50.20, "Metro", "Wife"),
            e(100.05, "Cafe Yesterday", "Husband", body = "Rs.100.05 debited at Cafe UPI Ref 987654").copy(dateInMillis = yesterday, sourceSender = "AX-ICICIB"),
            e(25.05, "Manual refund", "Shared", "CREDIT"),
            e(50.00, "Bank credit", "Shared", "CREDIT", "Acct XX070 is credited with Rs.50.00").copy(sourceSender = "AX-ICICIB"),
            e(999.0, "Foreign", "Husband").copy(currency = "USD"),
            e(500.0, "Untracked", "Shared", "CREDIT", "Acct XX071 is credited with Rs.500.00").copy(sourceSender = "AX-ICICIB")
        ))
    }

    private fun launch() { scenario = ActivityScenario.launch(MainActivity::class.java) }
    private fun node(tag: String) = ui.onNodeWithTag(tag, useUnmergedTree = true)
    private fun awaitText(tag: String, value: String) {
        ui.waitUntil(15000) {
            runCatching { node(tag).fetchSemanticsNode().config[SemanticsProperties.Text].any { it.text == value } }.getOrDefault(false)
        }
        node(tag).assertTextEquals(value)
    }
    private fun shot(name: String) {
        ui.waitForIdle()
        val bitmap = ui.onRoot().captureToImage().asAndroidBitmap()
        assertNotNull(bitmap)
        val dir = File(context.getExternalFilesDir(null), "audit-screenshots").apply { mkdirs() }
        val file = File(dir, "$name.png")
        file.outputStream().use { bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // Preserve evidence when Gradle uninstalls the dedicated test application.
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            android.os.ParcelFileDescriptor.AutoCloseInputStream(
                automation.executeShellCommand("mkdir -p /sdcard/Download/TwoFundsAudit")
            ).use { it.readBytes() }
            val streams = automation.executeShellCommandRw("dd of=/sdcard/Download/TwoFundsAudit/$name.png")
            android.os.ParcelFileDescriptor.AutoCloseOutputStream(streams[1]).use {
                assertTrue(bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            android.os.ParcelFileDescriptor.AutoCloseInputStream(streams[0]).use { it.readBytes() }
        }
        bitmap?.recycle()
    }

    @Test fun dashboardsProfilesSearchAndSmsPeriodReconcileToStoredRows() {
        seed(); launch()
        awaitText("home-value-Daily Spend", "₹500.30")
        awaitText("home-value-Credited Today", "₹75.05")
        awaitText("home-value-Daily Remaining", "₹-125.25")
        node("home-value-Monthly Spend").performScrollTo()
        awaitText("home-value-Monthly Spend", "₹600.35")
        awaitText("home-value-Monthly Remaining", "₹474.70")
        shot("home-totals")

        ui.onNodeWithText("Spend Radar").performClick()
        node("radar-utilization").performScrollTo()
        awaitText("radar-utilization", "141.8%")
        awaitText("radar-remaining", "₹125.25")
        ui.onNodeWithText("OVER BUDGET").assertExists()
        node("radar-remaining").performScrollTo().assertIsDisplayed()
        shot("radar-over-budget")
        ui.onNodeWithText("👩 Wife").performScrollTo().performClick()
        awaitText("radar-spent", "₹50.20")
        awaitText("radar-remaining", "₹125.25")
        awaitText("radar-utilization", "141.8%")

        ui.onNodeWithText("Home").performClick()
        ui.onNodeWithText("Weekly Spend").performScrollTo().performClick()
        ui.onNodeWithText("75.0% used").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("₹525.30").assertExists()
        ui.onNodeWithText("25.0% remaining").assertExists()
        shot("weekly-summary")
        ui.onNodeWithText("Thursday 🗓️").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("₹100.05").assertIsDisplayed()
        ui.onNodeWithText("₹500.30").assertIsDisplayed()
        ui.onNodeWithText("+₹75.05 credited").assertIsDisplayed()
        ui.onNodeWithText("4 transactions").assertIsDisplayed()
        shot("weekly-day-grid")

        ui.onNodeWithText("Home").performClick()
        ui.onNodeWithText("Monthly Spend").performScrollTo().performClick()
        awaitText("monthly-spent", "₹600.35")
        awaitText("monthly-credit", "+₹75.05")
        awaitText("monthly-net", "-₹525.30")
        node("monthly-search").performTextInput("Cafe")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        awaitText("monthly-spent", "₹550.15")
        awaitText("monthly-credit", "+₹0.00")
        awaitText("monthly-net", "-₹550.15")
        shot("monthly-filtered-summary")
        node("monthly-ledger").performScrollToNode(hasText("CATEGORY BREAKDOWN"))
        ui.onNodeWithText("₹550.15 (100.0%)").performScrollTo().assertIsDisplayed()
        shot("monthly-category-chart")

        ui.onNodeWithText("SMS Sync").performClick()
        awaitText("sms-summary", "2 matching SMS transactions • Spent ₹100.05 • Credited ₹50.00")
        ui.onNodeWithText("Today").performClick()
        awaitText("sms-summary", "1 matching SMS transactions • Spent ₹0.00 • Credited ₹50.00")
        shot("sms-today-filter")
        ui.onNodeWithText("Planner").performClick()
        ui.onNodeWithText("Rs.174.70 available").assertExists()
        ui.onNodeWithText("Plan exceeds budget by Rs.1,156.88.").assertExists()
        shot("planner-capped-by-weekly-budget")
    }

    @Test fun repeatedManualPurchasesAndDeletionUpdateBalances() {
        seed(); launch()
        awaitText("home-value-Daily Spend", "₹500.30")
        repeat(2) {
            ui.onNodeWithText("Add Transaction").performScrollTo().performClick()
            ui.onNodeWithText("Merchant").performTextInput("Repeat Cafe")
            ui.onNodeWithText("Amount").performTextInput("10.10")
            ui.onNodeWithText("Add", substring = false).performClick()
        }
        awaitText("home-value-Daily Spend", "₹520.50")
        assertEquals(2, runBlocking { db.expenseDao().getExpensesList().count { it.merchant == "Repeat Cafe" } })
        ui.onNodeWithText("View all ledger analytics").performScrollTo().performClick()
        ui.onAllNodesWithContentDescription("Delete").onFirst().performClick()
        ui.onNodeWithText("Delete", substring = false).performClick()
        ui.waitUntil(10000) { runBlocking { db.expenseDao().getExpensesList().count { it.merchant == "Repeat Cafe" } } == 1 }
        ui.onNodeWithText("Home").performClick()
        awaitText("home-value-Daily Spend", "₹510.40")
        awaitText("home-value-Daily Remaining", "₹-135.35")
        shot("after-delete-recalculation")
    }

    @Test fun emptyAccountHasNoSamplePurchasesAndZeroRideBudgetRemainsZero() {
        context.getSharedPreferences("spend_radar_prefs", 0).edit().putString("weekend_allowance", "0.00").putString("daily_pacing_limit", "0.00").commit()
        launch()
        awaitText("home-value-Daily Spend", "₹0.00")
        ui.onNodeWithText("No transactions recorded yet.").performScrollTo().assertExists()
        ui.onNodeWithText("Zomato Chicken Roll").assertDoesNotExist()
        ui.onNodeWithText("Planner").performClick()
        ui.onNodeWithText("Rs.0.00 available").assertExists()
        ui.onNodeWithText("Plan exceeds budget by Rs.1,331.58.").assertExists()
        shot("zero-budget-ride-planner")
    }

    @Test fun decimalAndZeroSettingsSurviveSavingAndActivityRecreation() {
        seed(); launch()
        awaitText("home-value-Daily Spend", "₹500.30")
        ui.onNodeWithText("Settings").performClick()
        ui.onNodeWithText("Weekday daily pacing").performScrollTo().performTextReplacement("-5")
        ui.onNodeWithText("Save settings").performScrollTo().assertIsNotEnabled()
        ui.onNodeWithText("Weekday daily pacing").performScrollTo().performTextReplacement("300.25")
        ui.onNodeWithText("Monthly budget").performScrollTo().performTextReplacement("1000.35")
        ui.onNodeWithText("Weekly budget").performScrollTo().performTextReplacement("700.45")
        ui.onNodeWithText("Weekend ride allowance").performScrollTo().performTextReplacement("0")
        ui.onNodeWithText("Save settings").performScrollTo().performClick()
        val prefs = context.getSharedPreferences("spend_radar_prefs", 0)
        assertEquals("300.25", prefs.getString("daily_pacing_limit", null))
        assertEquals("0.00", prefs.getString("weekend_allowance", null))
        ui.onNodeWithText("Home").performClick()
        awaitText("home-value-Daily Remaining", "₹-125.00")
        awaitText("home-value-Monthly Remaining", "₹475.05")
        scenario!!.recreate()
        awaitText("home-value-Daily Remaining", "₹-125.00")
        awaitText("home-value-Monthly Remaining", "₹475.05")
        shot("decimal-settings-after-recreation")
    }

    @Test fun persistedSmsErrorsAreCorrectedInTheRunningDashboard() {
        val recorded = now - 3600000
        runBlocking {
            db.expenseDao().insertAll(listOf(
                Expense(amount = 9000.0, currency = "INR", merchant = "Bank", category = "Other", dateInMillis = recorded,
                    originalSms = "Avl bal Rs.9000. Rs.100 debited from your account.", sourceSender = "AX-ICICIB", spentBy = "Wife"),
                Expense(amount = 50.0, currency = "INR", merchant = "Refund", category = "Refund", dateInMillis = recorded,
                    originalSms = "Acct XX070 credited Rs.50 refund for payment of Rs.100 to Cafe.", sourceSender = "AX-ICICIB"),
                Expense(amount = 500.0, currency = "INR", merchant = "Cafe", category = "Food", dateInMillis = recorded,
                    originalSms = "Payment of Rs.500 to Cafe failed. No money was debited.", sourceSender = "AX-ICICIB")
            ))
        }
        launch()
        awaitText("home-value-Daily Spend", "₹100.00")
        awaitText("home-value-Credited Today", "₹50.00")
        awaitText("home-value-Daily Remaining", "₹250.00")
        assertEquals(3, runBlocking { db.expenseDao().getExpensesList().size })
        shot("saved-sms-reconciled-dashboard")
    }
}
