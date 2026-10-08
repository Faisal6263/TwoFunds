package com.example

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone

class FinancialEdgeCaseTest {
    private fun row(amount: Double, date: Long, category: String = "Food", profile: String = "Husband") =
        Expense(amount = amount, currency = "INR", merchant = "Cafe", category = category, spentBy = profile,
            dateInMillis = date, originalSms = "manual-$amount-$date-$profile")
    private fun summary(rows: List<Expense>, now: Long) =
        buildBudgetSummary(rows, SpendMode.STANDARD, 1000.0, 300.0, 1000.0, 700.0, 1500.0, now)

    @Test fun sharesUseActualTotalBelowOneRupee() {
        assertEquals(1f, categoryShare(0.5, 0.5), 0.00001f)
        assertEquals(0.5f, categoryShare(0.25, 0.5), 0.00001f)
        assertEquals(0f, categoryShare(0.0, 0.0), 0.00001f)
    }

    @Test fun percentageAndGaugeHaveDifferentRoles() {
        val old = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals("150.0%", utilizationLabel(450.0, 300.0))
            assertEquals(1f, budgetProgress(450.0, 300.0), 0.00001f)
            assertEquals("No budget", utilizationLabel(100.0, 0.0))
            assertEquals("N/A", utilizationLabel(0.0, 0.0))
            assertEquals("33.3%", utilizationLabel(1.0, 3.0))
            assertEquals("1.01", formatMoney(1.005))
            assertEquals("0.30", formatMoney(0.1 + 0.2))
        } finally { Locale.setDefault(old) }
    }

    @Test fun zeroAndMissingBudgetsAreValidButNegativeAndNonfiniteAreRejected() {
        assertEquals(0.0, parseBudgetInput("0")!!, 0.00001)
        assertEquals(12.35, parseBudgetInput("12.345")!!, 0.00001)
        listOf("", "-1", "NaN", "Infinity", "1e309", "1.2.3").forEach { assertNull(parseBudgetInput(it)) }
        assertEquals(0f, budgetProgress(0.0, 0.0), 0.00001f)
    }

    @Test fun independentlyReconcilesDebitCreditCategoryAndProfileTotals() {
        val zone = ZoneId.systemDefault()
        fun at(day: Int, hour: Int = 10) = LocalDate.of(2026, 10, day).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
        val now = at(8, 12)
        val rows = listOf(row(100.10, at(5)), row(200.20, at(6), "Transport", "Wife"),
            row(300.30, at(7), "Groceries", "Shared"), row(0.10, at(8)), row(0.20, at(8), profile = "Wife"),
            row(10.05, at(8), "", ""), row(50.05, at(8)).copy(transactionType = "CREDIT"),
            row(25.25, at(8)).copy(transactionType = "CREDIT", originalSms = "Acct XX070 credited Rs.25.25", sourceSender = "AX-ICICIB"),
            row(999.0, at(8)).copy(currency = "USD"), row(800.0, at(9)),
            row(777.0, at(8)).copy(transactionType = "CREDIT", originalSms = "Other account credited Rs.777", sourceSender = "AX-ICICIB"))
        val s = summary(rows, now)
        assertEquals(610.95, s.monthlyTotal, 0.00001)
        assertEquals(75.30, s.monthlyCreditTotal, 0.00001)
        assertEquals(-535.65, s.monthlyNet, 0.00001)
        assertEquals(464.35, s.monthlyRemaining, 0.00001)
        assertEquals(164.35, s.weekRemaining, 0.00001)
        assertEquals(10.35, s.todayTotal, 0.00001)
        assertEquals(364.95, s.todayRemaining, 0.00001)
        assertEquals(299.40, s.weekdaySavings, 0.00001)
        // 1,799.40 released by the weekend policy is capped by the 164.35 weekly balance.
        assertEquals(164.35, s.weekendFundBalance, 0.00001)
        assertEquals(100.20, s.monthlyProfileTotals[SpenderProfile.HUSBAND]!!, 0.00001)
        assertEquals(200.40, s.monthlyProfileTotals[SpenderProfile.WIFE]!!, 0.00001)
        assertEquals(300.30, s.monthlyProfileTotals[SpenderProfile.SHARED]!!, 0.00001)
        assertEquals(10.05, s.monthlyUnassignedTotal, 0.00001)
        assertEquals(610.95, s.monthlyProfileTotals.values.sum() + s.monthlyUnassignedTotal, 0.00001)
        assertEquals(3, s.excludedTransactionCount)
        val categories = s.monthlyExpenses.filter { it.isDebit }.groupBy { normalizeBudgetCategory(it.category) }.mapValues { it.value.totalAmount() }
        assertEquals(100.40, categories["Food"]!!, 0.00001)
        assertEquals(610.95, categories.values.sum(), 0.00001)
        assertEquals(1.0, categories.values.sumOf { categoryShare(it, s.monthlyTotal).toDouble() }, 0.00001)
    }

    @Test fun leapMonthAndMidnightUseLocalCalendarBoundaries() {
        val zone = ZoneId.systemDefault()
        val feb = LocalDate.of(2028, 2, 29).atStartOfDay(zone).toInstant().toEpochMilli()
        val march = LocalDate.of(2028, 3, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val rows = listOf(row(10.0, feb), row(20.0, march - 1), row(30.0, march))
        assertEquals(30.0, summary(rows, march - 1).monthlyTotal, 0.00001)
        assertEquals(30.0, summary(rows, march).monthlyTotal, 0.00001)
        assertEquals(30.0, summary(rows, march).todayTotal, 0.00001)
        assertEquals(570.0, summary(rows, march).weekdaySavings, 0.00001)
    }

    @Test fun daylightSavingDayHasCalendarLengthRatherThanFixed24Hours() {
        val old = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            val zone = ZoneId.systemDefault()
            val start = LocalDate.of(2026, 3, 8).atStartOfDay(zone).toInstant().toEpochMilli()
            val end = LocalDate.of(2026, 3, 9).atStartOfDay(zone).toInstant().toEpochMilli()
            assertEquals(23 * 60 * 60 * 1000L, end - start)
            assertEquals(30.0, summary(listOf(row(10.0, start), row(20.0, end - 1), row(30.0, end)), end - 1).todayTotal, 0.00001)
        } finally { TimeZone.setDefault(old) }
    }

    @Test fun spendModesApplyOnlyTheirDocumentedMultiplier() {
        val now = System.currentTimeMillis()
        fun limit(mode: SpendMode) = buildBudgetSummary(emptyList(), mode, 123.45, 300.0, 1000.0, 700.0, 1500.0, now).activeDailyLimit
        assertEquals(210.0, limit(SpendMode.FRUGAL), 0.00001)
        assertEquals(300.0, limit(SpendMode.STANDARD), 0.00001)
        assertEquals(390.0, limit(SpendMode.SPLURGE), 0.00001)
        assertEquals(123.45, limit(SpendMode.CUSTOM), 0.00001)
    }

    @Test fun rideEstimateUsesRoundTripDistanceAndDoesNotInventMoney() {
        // 120 km / 40 km per litre * 100 rupees per litre + 200 food + 50 buffer = 550.
        val estimate = buildRideEstimate(60.0, 40.0, 100.0, 200.0, 50.0, 0.0)
        assertEquals(300.0, estimate.fuelCost, 0.00001)
        assertEquals(550.0, estimate.totalCost, 0.00001)
        assertEquals(-550.0, estimate.remaining, 0.00001)
        assertEquals(0.0, buildRideEstimate(0.0, 40.0, 100.0, 0.0, 0.0, 0.0).totalCost, 0.00001)
        assertEquals(1331.58, buildRideEstimate(60.0, 38.0, 105.0, 700.0, 300.0, 1500.0).totalCost, 0.00001)
    }

    @Test(expected = IllegalArgumentException::class) fun zeroMileageIsInvalid() {
        buildRideEstimate(60.0, 0.0, 100.0, 0.0, 0.0, 1500.0)
    }

    @Test fun upiMustMatchTheCompleteIdentity() {
        assertEquals("Food", normalizeBudgetCategory(" Food "))
        assertFalse("Faisal62632@ibl.other".containsTrackedCreditUpi())
        assertFalse("otherFaisal62632@ibl".containsTrackedCreditUpi())
        assertTrue("UPI: Faisal62632@ibl.".containsTrackedCreditUpi())
        assertTrue("UPI: Faisal62632@ibl on 8-Oct".containsTrackedCreditUpi())
    }
}
