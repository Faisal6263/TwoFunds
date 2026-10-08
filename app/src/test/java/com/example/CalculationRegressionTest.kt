package com.example

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class CalculationRegressionTest {
    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, 0, 0)
        }.timeInMillis

    private fun debit(amount: Double, time: Long, sms: String = "manual-$amount-$time") =
        Expense(amount = amount, currency = "INR", merchant = "Cafe", category = "Food",
            dateInMillis = time, originalSms = sms)

    private fun summary(rows: List<Expense>, now: Long, limit: Double = 300.0) =
        buildBudgetSummary(rows, SpendMode.STANDARD, 1000.0, limit, 30000.0, 4000.0, 1500.0, now)

    @Test fun manualCreditIsVisibleAndOffsetsSpending() {
        val now = at(2026, 10, 8)
        val rows = listOf(debit(100.0, now), debit(25.0, now).copy(transactionType = "CREDIT"))
        val s = summary(rows, now)
        assertEquals(25.0, s.todayCreditTotal, 0.001)
        assertEquals(225.0, s.todayRemaining, 0.001)
        assertEquals(2, s.todayExpenses.size)
    }

    @Test fun weekendSavingsUseCompletedDaysAndOffsetOverspending() {
        // Wednesday: Mon allowance 300 minus 500, Tue allowance 300 minus 100.
        // The completed days net to zero. Wednesday's unused 300 is not saved yet.
        val now = at(2026, 10, 7)
        val s = summary(listOf(debit(500.0, at(2026, 10, 5)), debit(100.0, at(2026, 10, 6))), now)
        assertEquals(0.0, s.weekdaySavings, 0.001)
        assertEquals(1500.0, s.weekendFundBalance, 0.001)
    }

    @Test fun weekendSpendingReducesAvailableFund() {
        val now = at(2026, 10, 11)
        val rows = (5..9).map { debit(300.0, at(2026, 10, it)) } + debit(500.0, at(2026, 10, 10))
        assertEquals(1000.0, summary(rows, now).weekendFundBalance, 0.001)
    }

    @Test fun overspendingRemainsVisibleAndZeroBudgetGaugeIsFull() {
        val now = at(2026, 10, 8)
        val s = summary(listOf(debit(450.0, now)), now)
        assertEquals(-150.0, s.todayRemaining, 0.001)
        assertEquals(1f, summary(listOf(debit(50.0, now)), now, 0.0).todayProgress, 0.001f)
    }

    @Test fun invalidAndForeignAmountsCannotPolluteRupeeTotals() {
        val now = at(2026, 10, 8)
        val rows = listOf(debit(20.0, now), debit(Double.NaN, now), debit(Double.POSITIVE_INFINITY, now),
            debit(-5.0, now), debit(10.0, now).copy(currency = "USD"),
            debit(99.0, now).copy(transactionType = "UNKNOWN"))
        assertEquals(20.0, summary(rows, now).todayTotal, 0.001)
    }

    @Test fun currencyAmountsRoundPerTransactionBeforeAdding() {
        val now = at(2026, 10, 8)
        assertEquals(2.02, summary(listOf(debit(1.005, now), debit(1.005, now)), now).todayTotal, 0.00001)
    }

    @Test fun futureAndBoundaryRowsDoNotLeakIntoCurrentReports() {
        val now = at(2026, 10, 8)
        val rows = listOf(debit(10.0, at(2026, 10, 8, 0)), debit(20.0, now),
            debit(999.0, now + 1), debit(40.0, at(2026, 10, 9, 0)),
            debit(50.0, at(2026, 11, 1, 0)), debit(60.0, at(2026, 9, 30, 23)))
        val s = summary(rows, now)
        assertEquals(30.0, s.todayTotal, 0.001)
        assertEquals(30.0, s.monthlyTotal, 0.001)
    }

    @Test fun sundayBelongsToWeekStartingPreviousMonday() {
        val now = at(2026, 11, 1)
        val rows = listOf(debit(10.0, at(2026, 10, 26, 0)), debit(20.0, at(2026, 11, 1)),
            debit(30.0, at(2026, 10, 25, 23)), debit(40.0, at(2026, 11, 2, 0)))
        val s = summary(rows, now)
        assertEquals(30.0, s.weekTotal, 0.001)
        assertEquals(20.0, s.monthlyTotal, 0.001)
    }

    @Test fun failedOrPendingPaymentIsNotAnExpense() {
        assertNull(parseExpenseFromSms("AX-ICICIB", "Payment of Rs.500 to Cafe failed. No money was debited.", 1000))
        assertNull(parseExpenseFromSms("AX-ICICIB", "Payment of Rs.500 to Cafe is pending.", 1000))
    }

    @Test fun balanceAmountIsNotTheTransactionAmount() {
        val sms = "Avl bal Rs.9000. Rs.100 debited from your account."
        assertEquals(100.0, parseExpenseFromSms("AX-ICICIB", sms, 1000)!!.amount, 0.001)
    }

    @Test fun creditedRefundMentioningOriginalPaymentRemainsCredit() {
        val sms = "Acct XX070 credited Rs.50 refund for payment of Rs.100 to Cafe."
        val e = parseExpenseFromSms("AX-ICICIB", sms, 1000)!!
        assertEquals("CREDIT", e.transactionType)
        assertEquals(50.0, e.amount, 0.001)
    }
}
