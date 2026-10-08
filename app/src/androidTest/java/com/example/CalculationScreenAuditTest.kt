package com.example

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

@RunWith(AndroidJUnit4::class)
class CalculationScreenAuditTest {
    @get:Rule val ui = createComposeRule()
    private fun at(year: Int, month: Int, day: Int) = Calendar.getInstance().apply {
        clear(); set(year, month - 1, day, 12, 0, 0)
    }.timeInMillis
    private fun summary(now: Long) = buildBudgetSummary(listOf(Expense(amount = 0.50, currency = "INR", merchant = "Tiny Cafe", category = "Food", dateInMillis = now, originalSms = "manual-tiny")),
        SpendMode.STANDARD, 1000.0, 300.0, 1000.0, 700.0, 1500.0, now)

    @Test fun fiftyPaiseIsOneHundredPercentAndGridFollowsTheReportingMonth() {
        val snapshot = mutableStateOf(summary(at(2028, 2, 29)))
        ui.setContent { MyApplicationTheme { MonthlyTransactionsScreen(snapshot.value, rememberNavController(), {}) } }
        ui.onNodeWithTag("monthly-spent").assertTextEquals("₹0.50")
        ui.onNodeWithTag("monthly-ledger").performScrollToNode(hasText("29 days • Tap a day to view transactions"))
        ui.onNodeWithText("29 days • Tap a day to view transactions").assertExists()
        ui.onNodeWithTag("monthly-ledger").performScrollToNode(hasText("CATEGORY BREAKDOWN"))
        ui.onNodeWithText("₹0.50 (100.0%)").assertExists()
        val gauges = ui.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).fetchSemanticsNodes()
        assertTrue(gauges.any { it.config[SemanticsProperties.ProgressBarRangeInfo].current == 1f })
        ui.runOnIdle { snapshot.value = summary(at(2028, 3, 1)) }
        ui.onNodeWithTag("monthly-ledger").performScrollToNode(hasText("31 days • Tap a day to view transactions"))
        ui.onNodeWithText("31 days • Tap a day to view transactions").assertExists()
    }

    @Test fun categoryWithZeroBudgetShowsSpentMoneyAsOverBudget() {
        val s = summary(at(2026, 10, 8))
        ui.setContent { MyApplicationTheme {
            MonthlyBudgetScreen(s, 1000.0, BudgetCategories.associateWith { 0.0 }, {}, { _, _ -> }, rememberNavController())
        } }
        ui.onNodeWithText("Spent ₹0.50 of ₹0.00").assertExists()
        val gauges = ui.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).fetchSemanticsNodes()
        assertTrue(gauges.any { it.config[SemanticsProperties.ProgressBarRangeInfo].current == 1f })
    }

    @Test fun openDayDetailsFollowTransactionChanges() {
        val now = at(2028, 2, 29)
        val snapshot = mutableStateOf(summary(now))
        ui.setContent { MyApplicationTheme { MonthlyTransactionsScreen(snapshot.value, rememberNavController(), {}) } }
        ui.onNodeWithTag("monthly-ledger").performScrollToNode(hasText("Tue • 29 Feb"))
        ui.onNodeWithText("Tue • 29 Feb").performClick()
        ui.onNodeWithText("1 transactions • Rs.0.50 spent • Rs.0.00 credited").assertExists()
        ui.runOnIdle {
            snapshot.value = buildBudgetSummary(emptyList(), SpendMode.STANDARD, 1000.0, 300.0, 1000.0, 700.0, 1500.0, now)
        }
        ui.onNodeWithText("0 transactions • Rs.0.00 spent • Rs.0.00 credited").assertExists()
        ui.onNodeWithText("No transactions found for this day.").assertExists()
    }
}
