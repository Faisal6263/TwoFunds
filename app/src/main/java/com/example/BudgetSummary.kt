package com.example

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class BudgetSummary(
    val activeDailyLimit: Double,
    val todayExpenses: List<Expense>,
    val todayTotal: Double,
    val todayRemaining: Double,
    val todayProgress: Float,
    val monthlyExpenses: List<Expense>,
    val monthlyTotal: Double,
    val monthlyRemaining: Double,
    val monthlyProgress: Float,
    val monthlyProfileTotals: Map<SpenderProfile, Double>,
    val monthName: String,
    val weekExpenses: List<Expense>,
    val weekTotal: Double,
    val weekRemaining: Double,
    val weekProgress: Float,
    val weekDailyAllocation: Double,
    val dailySpentByCalendarDay: Map<Int, Double>,
    val weekdaySavings: Double,
    val weekendFundBalance: Double,
    val todayDateLabel: String
)

fun buildBudgetSummary(
    expenses: List<Expense>,
    mode: SpendMode,
    customDailyLimit: Double,
    dailyPacingLimit: Double,
    monthlyBudget: Double,
    weeklyBudget: Double,
    weekendAllowance: Double,
    nowMillis: Long = System.currentTimeMillis()
): BudgetSummary {
    val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val activeDailyLimit = when (mode) {
        SpendMode.CUSTOM -> customDailyLimit
        else -> dailyPacingLimit * mode.multiplier
    }.coerceAtLeast(0.0)

    val todayStart = startOfDay(now).timeInMillis
    val tomorrowStart = Calendar.getInstance().apply {
        timeInMillis = todayStart
        add(Calendar.DAY_OF_YEAR, 1)
    }.timeInMillis
    val todayExpenses = expenses.filter { it.dateInMillis in todayStart until tomorrowStart }
    val todayTotal = todayExpenses.sumOf { it.amount }

    val monthStart = startOfMonth(now).timeInMillis
    val nextMonthStart = Calendar.getInstance().apply {
        timeInMillis = monthStart
        add(Calendar.MONTH, 1)
    }.timeInMillis
    val monthlyExpenses = expenses.filter { it.dateInMillis in monthStart until nextMonthStart }
    val monthlyTotal = monthlyExpenses.sumOf { it.amount }
    val monthlyProfileTotals = SpenderProfile.entries.associateWith { profile ->
        monthlyExpenses.filter { it.spentBy == profile.displayName }.sumOf { it.amount }
    }

    val weekStart = startOfWeek(now).timeInMillis
    val nextWeekStart = Calendar.getInstance().apply {
        timeInMillis = weekStart
        add(Calendar.DAY_OF_YEAR, 7)
    }.timeInMillis
    val weekExpenses = expenses.filter { it.dateInMillis in weekStart until nextWeekStart }
    val weekTotal = weekExpenses.sumOf { it.amount }
    val dailySpentByCalendarDay = mutableMapOf<Int, Double>().apply {
        put(Calendar.MONDAY, 0.0)
        put(Calendar.TUESDAY, 0.0)
        put(Calendar.WEDNESDAY, 0.0)
        put(Calendar.THURSDAY, 0.0)
        put(Calendar.FRIDAY, 0.0)
        put(Calendar.SATURDAY, 0.0)
        put(Calendar.SUNDAY, 0.0)
    }
    val expenseDay = Calendar.getInstance()
    weekExpenses.forEach { expense ->
        expenseDay.timeInMillis = expense.dateInMillis
        val day = expenseDay.get(Calendar.DAY_OF_WEEK)
        dailySpentByCalendarDay[day] = (dailySpentByCalendarDay[day] ?: 0.0) + expense.amount
    }

    val todayDay = now.get(Calendar.DAY_OF_WEEK)
    val weekdaySavings = listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)
        .filter { hasWeekdayStarted(it, todayDay) }
        .sumOf { day -> (activeDailyLimit - (dailySpentByCalendarDay[day] ?: 0.0)).coerceAtLeast(0.0) }

    return BudgetSummary(
        activeDailyLimit = activeDailyLimit,
        todayExpenses = todayExpenses,
        todayTotal = todayTotal,
        todayRemaining = (activeDailyLimit - todayTotal).coerceAtLeast(0.0),
        todayProgress = ratio(todayTotal, activeDailyLimit),
        monthlyExpenses = monthlyExpenses,
        monthlyTotal = monthlyTotal,
        monthlyRemaining = (monthlyBudget - monthlyTotal).coerceAtLeast(0.0),
        monthlyProgress = ratio(monthlyTotal, monthlyBudget),
        monthlyProfileTotals = monthlyProfileTotals,
        monthName = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(nowMillis)),
        weekExpenses = weekExpenses,
        weekTotal = weekTotal,
        weekRemaining = (weeklyBudget - weekTotal).coerceAtLeast(0.0),
        weekProgress = ratio(weekTotal, weeklyBudget),
        weekDailyAllocation = if (weeklyBudget > 0.0) weeklyBudget / 7.0 else 0.0,
        dailySpentByCalendarDay = dailySpentByCalendarDay,
        weekdaySavings = weekdaySavings,
        weekendFundBalance = weekendAllowance + weekdaySavings,
        todayDateLabel = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()).format(Date(nowMillis))
    )
}

fun normalizeBudgetCategory(category: String): String {
    return when (category.lowercase(Locale.ROOT)) {
        "petrol", "fuel", "ride", "rides", "bike", "two wheeler", "two-wheeler" -> "Rides"
        "transport", "auto-parsed" -> "Transport"
        "utility bills", "bills" -> "Utilities"
        else -> BudgetCategories.firstOrNull { it.equals(category, ignoreCase = true) } ?: "Other"
    }
}

private fun ratio(value: Double, limit: Double): Float =
    if (limit > 0.0) (value / limit).toFloat().coerceIn(0f, 1f) else 0f

private fun startOfDay(source: Calendar): Calendar =
    Calendar.getInstance().apply {
        timeInMillis = source.timeInMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

private fun startOfMonth(source: Calendar): Calendar =
    startOfDay(source).apply {
        set(Calendar.DAY_OF_MONTH, 1)
    }

private fun startOfWeek(source: Calendar): Calendar =
    startOfDay(source).apply {
        firstDayOfWeek = Calendar.MONDAY
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    }

private fun hasWeekdayStarted(day: Int, today: Int): Boolean =
    when (today) {
        Calendar.SATURDAY, Calendar.SUNDAY -> true
        else -> day <= today
    }
