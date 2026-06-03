package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SpendMode(val multiplier: Double, val displayName: String) {
    FRUGAL(0.7, "Frugal"),
    STANDARD(1.0, "Standard"),
    SPLURGE(1.3, "Splurge"),
    CUSTOM(1.5, "Custom")
}

val BudgetCategories = listOf("Food", "Transport", "Groceries", "Utilities", "Shopping", "Health", "Entertainment", "Rides", "Other")

val DefaultCategoryBudgets = linkedMapOf(
    "Food" to 7000.0,
    "Transport" to 3500.0,
    "Groceries" to 6500.0,
    "Utilities" to 4500.0,
    "Shopping" to 3500.0,
    "Health" to 2000.0,
    "Entertainment" to 2000.0,
    "Rides" to 2500.0,
    "Other" to 2000.0
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = DefaultDatabase.getInstance(application)

    private val repository = ExpenseRepository(db.expenseDao())

    val uiState: StateFlow<List<Expense>> = repository.allExpenses
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val prefs = application.getSharedPreferences("spend_radar_prefs", android.content.Context.MODE_PRIVATE)

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _currentMode = MutableStateFlow(
        try {
            SpendMode.valueOf(prefs.getString("current_mode", SpendMode.STANDARD.name) ?: SpendMode.STANDARD.name)
        } catch (e: Exception) {
            SpendMode.STANDARD
        }
    )
    val currentMode: StateFlow<SpendMode> = _currentMode.asStateFlow()

    private val _currentSpender = MutableStateFlow(
        try {
            SpenderProfile.valueOf(prefs.getString("current_spender", SpenderProfile.HUSBAND.name) ?: SpenderProfile.HUSBAND.name)
        } catch (e: Exception) {
            SpenderProfile.HUSBAND
        }
    )
    val currentSpender: StateFlow<SpenderProfile> = _currentSpender.asStateFlow()

    private val _customDailyLimit = MutableStateFlow(
        prefs.getFloat("custom_daily_limit", 1000f).toDouble()
    )
    val customDailyLimit: StateFlow<Double> = _customDailyLimit.asStateFlow()

    private val _dailyPacingLimit = MutableStateFlow(
        prefs.getFloat("daily_pacing_limit", 300f).toDouble()
    )
    val dailyPacingLimit: StateFlow<Double> = _dailyPacingLimit.asStateFlow()

    private val _monthlyBudget = MutableStateFlow(
        prefs.getFloat("monthly_budget", 30000f).toDouble()
    )
    val monthlyBudget: StateFlow<Double> = _monthlyBudget.asStateFlow()

    private val _weeklyBudget = MutableStateFlow(
        prefs.getFloat("weekly_budget", 4000f).toDouble()
    )
    val weeklyBudget: StateFlow<Double> = _weeklyBudget.asStateFlow()

    private val _weekendAllowance = MutableStateFlow(
        prefs.getFloat("weekend_allowance", 1500f).toDouble()
    )
    val weekendAllowance: StateFlow<Double> = _weekendAllowance.asStateFlow()

    private val _categoryBudgets = MutableStateFlow(loadCategoryBudgets())
    val categoryBudgets: StateFlow<Map<String, Double>> = _categoryBudgets.asStateFlow()

    fun setMode(mode: SpendMode) {
        _currentMode.value = mode
        prefs.edit().putString("current_mode", mode.name).apply()
    }

    fun setCurrentSpender(profile: SpenderProfile) {
        _currentSpender.value = profile
        prefs.edit().putString("current_spender", profile.name).apply()
    }

    fun setCustomDailyLimit(limit: Double) {
        _customDailyLimit.value = limit
        prefs.edit().putFloat("custom_daily_limit", limit.toFloat()).apply()
    }

    fun setDailyPacingLimit(limit: Double) {
        _dailyPacingLimit.value = limit
        prefs.edit().putFloat("daily_pacing_limit", limit.toFloat()).apply()
    }

    fun setMonthlyBudget(budget: Double) {
        _monthlyBudget.value = budget
        prefs.edit().putFloat("monthly_budget", budget.toFloat()).apply()
    }

    fun setWeeklyBudget(budget: Double) {
        _weeklyBudget.value = budget
        prefs.edit().putFloat("weekly_budget", budget.toFloat()).apply()
    }

    fun setWeekendAllowance(allowance: Double) {
        _weekendAllowance.value = allowance
        prefs.edit().putFloat("weekend_allowance", allowance.toFloat()).apply()
    }

    fun setCategoryBudget(category: String, budget: Double) {
        val updated = _categoryBudgets.value.toMutableMap()
        updated[category] = budget
        _categoryBudgets.value = BudgetCategories.associateWith { updated[it] ?: 0.0 }
        saveCategoryBudgets(_categoryBudgets.value)
    }

    private fun loadCategoryBudgets(): Map<String, Double> {
        val saved = prefs.getString("category_budgets", null) ?: return DefaultCategoryBudgets
        val parsed = saved.split("|")
            .mapNotNull { entry ->
                val parts = entry.split(":", limit = 2)
                val amount = parts.getOrNull(1)?.toDoubleOrNull()
                val category = parts.getOrNull(0)
                if (category.isNullOrBlank() || amount == null) null else category to amount
            }
            .toMap()
        return BudgetCategories.associateWith { parsed[it] ?: DefaultCategoryBudgets[it] ?: 0.0 }
    }

    private fun saveCategoryBudgets(budgets: Map<String, Double>) {
        val encoded = BudgetCategories.joinToString("|") { category ->
            "$category:${budgets[category] ?: 0.0}"
        }
        prefs.edit().putString("category_budgets", encoded).apply()
    }

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    fun syncExpensesFromSms(smsList: List<SmsMessageData>) {
        if (_isSyncing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isSyncing.value = true

            try {
                val currentExpenses = uiState.value.map { it.originalSms }.toSet()
                val deletedSmsSet = prefs.getStringSet("deleted_sms", emptySet()) ?: emptySet()
                val candidates = smsList.filter { !currentExpenses.contains(it.body) && !deletedSmsSet.contains(it.body) }
                _syncMessage.value = "Read ${smsList.size} SMS. Parsing ${candidates.size} new messages locally..."

                val expenses = candidates.mapNotNull { item ->
                    parseExpenseFromSms(item.sender, item.body, item.date)
                        ?.copy(spentBy = _currentSpender.value.displayName)
                }

                if (expenses.isNotEmpty()) {
                    repository.insertAll(expenses)
                }

                _syncMessage.value = "Local sync complete. Found ${expenses.size} new expenses."
            } catch (e: Exception) {
                e.printStackTrace()
                _syncMessage.value = "Error during sync: ${e.message}"
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun addExpense(expense: Expense) {
        viewModelScope.launch {
            repository.insert(expense.copy(spentBy = _currentSpender.value.displayName))
        }
    }

    fun deleteExpense(id: Int) {
        viewModelScope.launch {
            val expense = uiState.value.find { it.id == id }
            if (expense != null && expense.originalSms.isNotBlank()) {
                val deletedSmsSet = prefs.getStringSet("deleted_sms", mutableSetOf()) ?: mutableSetOf()
                val newSet = deletedSmsSet.toMutableSet()
                newSet.add(expense.originalSms)
                prefs.edit().putStringSet("deleted_sms", newSet).apply()
            }
            repository.deleteById(id)
        }
    }
}
