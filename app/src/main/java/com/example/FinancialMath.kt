package com.example

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/** INR amounts are rounded to paise, HALF_UP, per recorded transaction. */
fun roundMoney(value: Double): Double =
    BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toDouble()

fun Iterable<Expense>.totalAmount(): Double =
    fold(BigDecimal.ZERO) { total, row ->
        total + BigDecimal.valueOf(row.amount).setScale(2, RoundingMode.HALF_UP)
    }.toDouble()

fun formatMoney(amount: Double): String = if (amount.isFinite())
    String.format(Locale.getDefault(), "%,.2f", roundMoney(amount)) else "Invalid amount"

fun formatMoney(amount: Float): String = formatMoney(amount.toDouble())

fun moneyInput(amount: Double): String = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).toPlainString()

fun parseBudgetInput(input: String): Double? = input.toDoubleOrNull()
    ?.takeIf { it.isFinite() && it >= 0.0 }?.let(::roundMoney)

fun safeBudget(value: Double): Double = if (value.isFinite() && value >= 0.0) roundMoney(value) else 0.0

/** A gauge is bounded; the separate percentage label preserves utilization above 100%. */
fun budgetProgress(used: Double, limit: Double): Float = when {
    limit > 0.0 -> (used / limit).coerceIn(0.0, 1.0).toFloat()
    used > 0.0 -> 1f
    else -> 0f
}

fun utilizationLabel(used: Double, limit: Double): String =
    if (limit > 0.0) String.format(Locale.getDefault(), "%.1f%%", used.coerceAtLeast(0.0) / limit * 100.0)
    else if (used > 0.0) "No budget" else "N/A"

fun categoryShare(amount: Double, total: Double): Float =
    if (total > 0.0) (amount / total).coerceIn(0.0, 1.0).toFloat() else 0f

val Expense.hasValidRupeeAmount: Boolean
    get() = amount.isFinite() && amount >= 0.0 && dateInMillis > 0 &&
        currency.trim().uppercase(Locale.ROOT) in setOf("INR", "₹") && (isDebit || isCredit)

val Expense.isReportableTransaction: Boolean
    get() = hasValidRupeeAmount && (isDebit || isTrackedCredit ||
        (isCredit && originalSms.startsWith("manual-")))

fun reportableTransactions(rows: List<Expense>, nowMillis: Long): List<Expense> = rows
    .filter { it.isReportableTransaction && it.dateInMillis <= nowMillis }
    .map { it.copy(amount = roundMoney(it.amount)) }
    .sortedByDescending { it.dateInMillis }

fun Expense.matchesProfile(profile: SpenderProfile): Boolean =
    spentBy.trim().equals(profile.displayName, ignoreCase = true)

fun spendingInsight(summary: BudgetSummary): String = when {
    summary.todayRemaining < 0 -> "Daily budget exceeded by ₹${formatMoney(-summary.todayRemaining)}."
    summary.weekRemaining < 0 -> "Weekly budget exceeded by ₹${formatMoney(-summary.weekRemaining)}."
    summary.monthlyRemaining < 0 -> "Monthly budget exceeded by ₹${formatMoney(-summary.monthlyRemaining)}."
    summary.monthlyExpenses.isEmpty() -> "No transactions recorded this month. Balances are based on configured budgets."
    else -> "Within recorded budgets. Weekend fund estimate: ₹${formatMoney(summary.weekendFundBalance)}."
}
