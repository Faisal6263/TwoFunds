package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                val body = sms.displayMessageBody
                val address = sms.displayOriginatingAddress

                // Basic check to minimize AI calls
                if (body.contains("spent", true) || 
                    body.contains("debited", true) || 
                    body.contains("rs", true) || 
                    body.contains("inr", true) ||
                    body.contains("paid", true) ||
                    body.contains("amount", true)
                ) {
                    val messageTimestamp = sms.timestampMillis
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val db = DefaultDatabase.getInstance(context)
                            val repository = ExpenseRepository(db.expenseDao())
                            var savedByAi = false
                            
                            if (BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "null") {
                                val prompt = """
                                    You are an AI assistant parsing SMS.
                                    Text: "$body"
                                    Is this a new expense transaction (debit, spent, transfer, paid)? If yes, extract:
                                    - amount (number)
                                    - currency (string)
                                    - merchant (the entity or person to whom money was paid or who was credited. In standard Indian debit/UPI SMS messages, this is usually the person or brand whose name comes before the word 'Credited' or 'credited', e.g., in 'Ramesh Kumar Credited' the merchant is 'Ramesh Kumar'. Or after 'to' or 'at', e.g. 'paid to Swiggy'. Extract the name of this person or vendor. Do NOT use bank names like SBI or HDFC as the merchant.)
                                    - category (string - Food, Transport, Shopping, Entertainment, Groceries, Utilities, Health, Other)
                                    Return as JSON with those fields.
                                """.trimIndent()

                                val requestBody = GenerateContentRequest(
                                    contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                                    generationConfig = GenerationConfig(
                                        temperature = 0.0f,
                                        responseFormat = ResponseFormat(
                                            text = ResponseFormatText(
                                                mimeType = "application/json",
                                                schema = buildJsonObject {
                                                    put("type", "OBJECT")
                                                    putJsonObject("properties") {
                                                        putJsonObject("amount") { put("type", "NUMBER") }
                                                        putJsonObject("currency") { put("type", "STRING") }
                                                        putJsonObject("merchant") { put("type", "STRING") }
                                                        putJsonObject("category") { put("type", "STRING") }
                                                    }
                                                }
                                            )
                                        )
                                    )
                                )
                                val response = RetrofitClient.service.generateContent(BuildConfig.GEMINI_API_KEY, requestBody)
                                val text = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                            
                                if (text != null) {
                                    val obj = Json.parseToJsonElement(text).jsonObject
                                    val amountStr = obj["amount"]?.jsonPrimitive?.content
                                    val amount = amountStr?.toDoubleOrNull() ?: 0.0
                                    val currency = obj["currency"]?.jsonPrimitive?.content ?: ""
                                    var merchant = obj["merchant"]?.jsonPrimitive?.content ?: ""
                                    if (merchant.isBlank() || merchant.equals("Unknown", ignoreCase = true) || merchant.contains("bank", ignoreCase = true) || merchant.equals(address, ignoreCase = true)) {
                                        merchant = parseMerchantFromText(address, body)
                                    }
                                    val category = obj["category"]?.jsonPrimitive?.content ?: "Other"
                                
                                    if (amount > 0) {
                                        repository.insert(
                                            Expense(
                                                amount = amount,
                                                currency = currency,
                                                merchant = merchant,
                                                category = category,
                                                dateInMillis = messageTimestamp,
                                                originalSms = body
                                            )
                                        )
                                        savedByAi = true
                                    }
                                }
                            }

                            if (!savedByAi) {
                                parseExpenseFromSmsFallback(address, body, messageTimestamp)?.let { expense ->
                                    repository.insert(expense)
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            try {
                                val repository = ExpenseRepository(DefaultDatabase.getInstance(context).expenseDao())
                                parseExpenseFromSmsFallback(address, body, messageTimestamp)?.let { expense ->
                                    repository.insert(expense)
                                }
                            } catch (fallbackError: Exception) {
                                fallbackError.printStackTrace()
                            }
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
        }
    }
}
