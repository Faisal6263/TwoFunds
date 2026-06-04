package com.example

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class BudgetSummaryUnitTest {
    @Test
    fun buildsSharedDailyWeeklyAndMonthlyTotals() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.JUNE, 3, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val monday = Calendar.getInstance().apply {
            timeInMillis = now.timeInMillis
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 10)
        }
        val today = Calendar.getInstance().apply {
            timeInMillis = now.timeInMillis
            set(Calendar.HOUR_OF_DAY, 11)
        }
        val previousMonth = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 31, 11, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val expenses = listOf(
            Expense(amount = 100.0, currency = "INR", merchant = "Cafe", category = "Food", dateInMillis = monday.timeInMillis, originalSms = "monday"),
            Expense(amount = 250.0, currency = "INR", merchant = "Metro", category = "Transport", dateInMillis = today.timeInMillis, originalSms = "today"),
            Expense(amount = 999.0, currency = "INR", merchant = "Old", category = "Other", dateInMillis = previousMonth.timeInMillis, originalSms = "old")
        )

        val summary = buildBudgetSummary(
            expenses = expenses,
            mode = SpendMode.STANDARD,
            customDailyLimit = 1200.0,
            dailyPacingLimit = 500.0,
            monthlyBudget = 5000.0,
            weeklyBudget = 2100.0,
            weekendAllowance = 700.0,
            nowMillis = now.timeInMillis
        )

        assertEquals(500.0, summary.activeDailyLimit, 0.01)
        assertEquals(250.0, summary.todayTotal, 0.01)
        assertEquals(350.0, summary.weekTotal, 0.01)
        assertEquals(350.0, summary.monthlyTotal, 0.01)
        assertEquals(300.0, summary.weekDailyAllocation, 0.01)
    }
}
