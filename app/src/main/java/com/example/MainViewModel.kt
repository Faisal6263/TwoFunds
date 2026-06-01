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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class SpendMode(val multiplier: Double, val displayName: String) {
    FRUGAL(0.7, "Frugal"),
    STANDARD(1.0, "Standard"),
    SPLURGE(1.3, "Splurge"),
    CUSTOM(1.5, "Custom")
}

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

    private val _customDailyLimit = MutableStateFlow(
        prefs.getFloat("custom_daily_limit", 1000f).toDouble()
    )
    val customDailyLimit: StateFlow<Double> = _customDailyLimit.asStateFlow()

    private val _weeklyBudget = MutableStateFlow(
        prefs.getFloat("weekly_budget", 4000f).toDouble()
    )
    val weeklyBudget: StateFlow<Double> = _weeklyBudget.asStateFlow()

    fun setMode(mode: SpendMode) {
        _currentMode.value = mode
        prefs.edit().putString("current_mode", mode.name).apply()
    }

    fun setCustomDailyLimit(limit: Double) {
        _customDailyLimit.value = limit
        prefs.edit().putFloat("custom_daily_limit", limit.toFloat()).apply()
    }

    fun setWeeklyBudget(budget: Double) {
        _weeklyBudget.value = budget
        prefs.edit().putFloat("weekly_budget", budget.toFloat()).apply()
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
            
            // Pre-filter to finding likely financial transactions to avoid API limits or irrelevant parsing
            val filterKeywords = listOf("spent", "debited", "paid", "rs.", "rs", "inr", "$", "amount", "credited", "a/c", "acct", "txn", "transaction", "payment")
            val filteredList = smsList.filter { sms ->
                filterKeywords.any { sms.body.contains(it, ignoreCase = true) } ||
                sms.sender.contains("BK", ignoreCase = true) ||
                sms.sender.contains("BANK", ignoreCase = true)
            }
            
            _syncMessage.value = "Read ${smsList.size} SMS. Filtered to ${filteredList.size} transactions..."
            
            if (filteredList.isEmpty()) {
                _syncMessage.value = "Read ${smsList.size} messages, but no financial transactions found."
                _isSyncing.value = false
                return@launch
            }

            try {
                // Batch messages by 10 to avoid huge context or rate limits/timeouts
                val batches = filteredList.chunked(10)
                var newExpensesCount = 0
                
                val currentExpenses = uiState.value.map { it.originalSms }.toSet()
                val deletedSmsSet = prefs.getStringSet("deleted_sms", emptySet()) ?: emptySet()

                for ((index, batch) in batches.withIndex()) {
                    _syncMessage.value = "Analyzing SMS batch ${index + 1} of ${batches.size}..."
                    // Filter batch items that are already in DB based on originalSms or deleted
                    val newBatch = batch.filter { !currentExpenses.contains(it.body) && !deletedSmsSet.contains(it.body) }
                    
                    if (newBatch.isEmpty()) continue

                    val batchJson = Json.encodeToString(
                        kotlinx.serialization.serializer(),
                        newBatch.map { mapOf("sender" to it.sender, "body" to it.body, "date" to it.date.toString()) }
                    )

                    val prompt = """
                        You are an AI assistant helping to categorize expenses from SMS messages.
                        Analyze the following JSON array of SMS messages.
                        Identify messages that are CLEARLY expense transactions (e.g. money debited, spent, paid).
                        Ignore OTPs, promotional messages, or credited/received messages.
                        For each expense transaction, extract:
                        - amount (as a number)
                        - currency (e.g., INR, USD, EUR)
                        - merchant (the entity or person to whom money was paid or who was credited. In standard Indian debit/UPI SMS messages, this is usually the person or brand whose name comes before the word 'Credited' or 'credited', e.g., in 'Ramesh Kumar Credited' the merchant is 'Ramesh Kumar'. Or after 'to' or 'at', e.g. 'paid to Swiggy'. Extract the name of this person or vendor. Do NOT use bank names like SBI or HDFC as the merchant.)
                        - category (one of: Food, Transport, Utilities, Shopping, Entertainment, Groceries, Health, Other)
                        - originalSms (the exact unmodified body of the message)
                        - dateInMillis (the provided date in the input object)
                        
                        Messages:
                        $batchJson
                    """.trimIndent()

                    val requestBody = GenerateContentRequest(
                        contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                        generationConfig = GenerationConfig(
                            temperature = 0.0f,
                            responseFormat = ResponseFormat(
                                text = ResponseFormatText(
                                    mimeType = "application/json",
                                    schema = buildJsonObject {
                                        put("type", "ARRAY")
                                        putJsonObject("items") {
                                            put("type", "OBJECT")
                                            putJsonObject("properties") {
                                                putJsonObject("amount") { put("type", "NUMBER") }
                                                putJsonObject("currency") { put("type", "STRING") }
                                                putJsonObject("merchant") { put("type", "STRING") }
                                                putJsonObject("category") { put("type", "STRING") }
                                                putJsonObject("originalSms") { put("type", "STRING") }
                                                putJsonObject("dateInMillis") { put("type", "STRING") }
                                            }
                                        }
                                    }
                                )
                            )
                        )
                    )
                    
                    try {
                        if (BuildConfig.GEMINI_API_KEY.isEmpty() || BuildConfig.GEMINI_API_KEY == "null") {
                            throw Exception("Missing API Key")
                        }
                        val response = RetrofitClient.service.generateContent(BuildConfig.GEMINI_API_KEY, requestBody)
                        val text = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                        
                        if (text != null) {
                            val jsonArray = Json.parseToJsonElement(text).jsonArray
                            val expenses = mutableListOf<Expense>()
                            for (item in jsonArray) {
                                val obj = item.jsonObject
                                val amountStr = obj["amount"]?.jsonPrimitive?.content ?: "0"
                                val amount = amountStr.toDoubleOrNull() ?: 0.0
                                val currency = obj["currency"]?.jsonPrimitive?.content ?: ""
                                val merchant = obj["merchant"]?.jsonPrimitive?.content ?: "Unknown"
                                val category = obj["category"]?.jsonPrimitive?.content ?: "Other"
                                val originalSms = obj["originalSms"]?.jsonPrimitive?.content ?: ""
                                val dateInMillisStr = obj["dateInMillis"]?.jsonPrimitive?.content ?: "0"
                                val dateInMillis = dateInMillisStr.toLongOrNull() ?: System.currentTimeMillis()
                                
                                if (amount > 0 && originalSms.isNotBlank()) {
                                    expenses.add(
                                        Expense(
                                            amount = amount,
                                            currency = currency,
                                            merchant = merchant,
                                            category = category,
                                            dateInMillis = dateInMillis,
                                            originalSms = originalSms
                                        )
                                    )
                                }
                            }
                            if (expenses.isNotEmpty()) {
                                repository.insertAll(expenses)
                                newExpensesCount += expenses.size
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        val expenses = newBatch.mapNotNull { item ->
                            parseExpenseFromSmsFallback(item.sender, item.body, item.date)
                        }
                        if (expenses.isNotEmpty()) {
                            repository.insertAll(expenses)
                            newExpensesCount += expenses.size
                        }
                    }
                }
                
                _syncMessage.value = "Sync complete. Found $newExpensesCount new expenses."
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
            repository.insert(expense)
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
