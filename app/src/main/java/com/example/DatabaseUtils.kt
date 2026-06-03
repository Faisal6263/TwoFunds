package com.example

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE expenses ADD COLUMN spentBy TEXT NOT NULL DEFAULT 'Husband'")
    }
}

object DefaultDatabase {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getInstance(context: Context): AppDatabase {
        return INSTANCE ?: synchronized(this) {
            val instance = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "expenses-database"
            )
            .addMigrations(MIGRATION_2_3)
            .fallbackToDestructiveMigration()
            .build()
            INSTANCE = instance
            instance
        }
    }
}

fun parseMerchantFromText(sender: String, body: String): String {
    val noiseWords = setOf(
        "has", "been", "was", "is", "be", "to", "for", "by", "on", "at", "in", "and", "of", "your", "my",
        "account", "acct", "vpa", "ref", "txn", "transaction", "payment", "amount", "balance", "limit", 
        "with", "from", "debited", "credited", "successfully", "paid", "spent", "transfer", "transferred", 
        "card", "rs", "inr", "upi", "no", "number", "code", "date", "sec", "time", "id", "via", "through", 
        "using", "towards", "into", "the", "an", "a", "not", "now", "here", "dear", "customer", "user"
    )

    fun cleanCandidate(candidate: String): String {
        // Strip punctuation like parentheses, commas, colons, semi-colons, dots, etc.
        val baseStr = candidate.replace(Regex("[()\\.,:;\"'\\-_/\\\\]"), " ")
        val words = baseStr.split(Regex("\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
        val remainingWords = words.filter { word ->
            val w = word.lowercase()
            !noiseWords.contains(w) && w.length > 1 && !w.any { it.isDigit() }
        }
        return remainingWords.joinToString(" ") { it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() } }.trim()
    }

    // Try finding names immediately before "credited" (Ramesh Kumar credited)
    // E.g. "... Ramesh Kumar credited."
    val beforeCreditedRegex = Regex("(?i)\\b([A-Za-z ]{3,50})\\s+credited")
    val beforeCreditedMatches = beforeCreditedRegex.findAll(body)
    for (m in beforeCreditedMatches) {
        val candidate = m.groupValues[1]
        val cleaned = cleanCandidate(candidate)
        if (cleaned.length > 2) {
            return cleaned
        }
    }

    // Try finding "credited to [Name]"
    val creditedToRegex = Regex("(?i)credited\\s+to\\s+([A-Za-z ]{3,50})")
    val creditedToMatches = creditedToRegex.findAll(body)
    for (m in creditedToMatches) {
        val candidate = m.groupValues[1]
        val cleaned = cleanCandidate(candidate)
        if (cleaned.length > 2) {
            return cleaned
        }
    }

    // Try finding "paid to [Name]" / "sent to [Name]" / "transfer to [Name]" / "transferred to [Name]"
    val paidToRegex = Regex("(?i)(?:paid\\s+to|sent\\s+to|transfer\\s+to|transferred\\s+to)\\s+([A-Za-z ]{3,50})")
    val paidToMatches = paidToRegex.findAll(body)
    for (m in paidToMatches) {
        val candidate = m.groupValues[1]
        val cleaned = cleanCandidate(candidate)
        if (cleaned.length > 2) {
            return cleaned
        }
    }

    // Try finding "spent at [Name]" / "spent on [Name]" / "payment at [Name]"
    val spentAtRegex = Regex("(?i)(?:spent\\s+at|spent\\s+on|payment\\s+at|txn\\s+at)\\s+([A-Za-z ]{3,50})")
    val spentAtMatches = spentAtRegex.findAll(body)
    for (m in spentAtMatches) {
        val candidate = m.groupValues[1]
        val cleaned = cleanCandidate(candidate)
        if (cleaned.length > 2) {
            return cleaned
        }
    }

    // If nothing else works, clean up the sender ID (e.g. "MD-SBI" -> "SBI")
    val cleanedSender = sender.replace(Regex("^[A-Z][A-Z]-"), "").trim()
    val senderCleaned = cleanedSender.uppercase()
    return if (senderCleaned.isNotBlank()) {
        when {
            senderCleaned.contains("SBI") -> "SBI"
            senderCleaned.contains("HDFC") -> "HDFC"
            senderCleaned.contains("ICICI") -> "ICICI"
            senderCleaned.contains("AXIS") -> "AXIS"
            senderCleaned.contains("KOTAK") -> "KOTAK"
            else -> senderCleaned
        }
    } else "Retail Bank"
}

fun looksLikeFinancialSms(sender: String, body: String): Boolean {
    val text = body.lowercase()
    val senderText = sender.lowercase()
    val hasMoneyToken = Regex("(?i)(?:rs\\.?|inr|\\u20B9)\\s*[0-9]").containsMatchIn(body)
    val senderLooksFinancial = listOf("bank", "bk", "hdfc", "sbi", "icici", "axis", "kotak", "upi", "paytm", "gpay", "phonepe")
        .any { senderText.contains(it) }
    val textLooksFinancial = listOf("debited", "paid", "spent", "txn", "transaction", "a/c", "acct", "upi", "card")
        .any { text.contains(it) }

    return hasMoneyToken && (senderLooksFinancial || textLooksFinancial)
}

fun parseExpenseFromSms(sender: String, body: String, dateInMillis: Long): Expense? {
    if (!looksLikeFinancialSms(sender, body) || !isExpenseDebit(body)) return null

    val amount = extractExpenseAmount(body) ?: return null
    val merchant = parseMerchantFromText(sender, body)

    return Expense(
        amount = amount,
        currency = inferCurrency(body),
        merchant = merchant,
        category = inferCategory(merchant, body),
        dateInMillis = dateInMillis,
        originalSms = body
    )
}

private fun isExpenseDebit(body: String): Boolean {
    val text = body.lowercase()
    val debitKeywords = listOf(
        "debited", "debit", "spent", "paid", "sent", "transferred", "withdrawn",
        "deducted", "purchase", "payment of", "txn of", "transaction of", "charged"
    )
    val hasDebitKeyword = debitKeywords.any { text.contains(it) } ||
        Regex("(?i)\\bdr\\b").containsMatchIn(body)

    if (!hasDebitKeyword) return false

    val creditOnlyPatterns = listOf(
        Regex("(?i)credited\\s+(?:to|into)\\s+your"),
        Regex("(?i)has\\s+been\\s+credited"),
        Regex("(?i)received\\s+(?:rs\\.?|inr|\\u20B9)"),
        Regex("(?i)(?:refund|cashback|reversal|reversed|salary|interest)\\b")
    )
    val hasDebitAction = listOf("debited", "debit", "spent", "paid", "sent", "withdrawn", "deducted")
        .any { text.contains(it) }
    val isCreditOnly = creditOnlyPatterns.any { it.containsMatchIn(body) } && !hasDebitAction

    return !isCreditOnly
}

private data class AmountCandidate(val amount: Double, val score: Int)

private fun extractExpenseAmount(body: String): Double? {
    val amountPatterns = listOf(
        Regex("(?i)(?:rs\\.?|inr|\\u20B9)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)"),
        Regex("(?i)([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s*(?:rs\\.?|inr)"),
        Regex("(?i)(?:amount|amt|payment|txn|transaction|purchase|debited)\\s*(?:of|by|for|is|:)?\\s*(?:rs\\.?|inr|\\u20B9)?\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)")
    )

    val candidates = amountPatterns.flatMap { regex ->
        regex.findAll(body).mapNotNull { match ->
            val rawAmount = match.groupValues.getOrNull(1).orEmpty().replace(",", "")
            val amount = rawAmount.toDoubleOrNull() ?: return@mapNotNull null
            AmountCandidate(amount, scoreAmountCandidate(body, match.range.first))
        }.toList()
    }

    return candidates
        .filter { it.amount > 0.0 }
        .maxByOrNull { it.score }
        ?.amount
}

private fun scoreAmountCandidate(body: String, index: Int): Int {
    val start = (index - 45).coerceAtLeast(0)
    val end = (index + 45).coerceAtMost(body.length)
    val window = body.substring(start, end).lowercase()
    var score = 0

    if (listOf("debited", "debit", "spent", "paid", "payment", "txn", "transaction", "purchase", "withdrawn").any { window.contains(it) }) {
        score += 10
    }
    if (listOf("balance", "available", "avl", "limit", "due", "outstanding").any { window.contains(it) }) {
        score -= 8
    }

    return score
}

private fun inferCurrency(body: String): String =
    when {
        Regex("(?i)\\busd\\b|\\$").containsMatchIn(body) -> "USD"
        Regex("(?i)\\beur\\b").containsMatchIn(body) -> "EUR"
        else -> "INR"
    }

private fun inferCategory(merchant: String, body: String): String {
    val text = "$merchant $body".lowercase()
    return when {
        listOf("swiggy", "zomato", "restaurant", "cafe", "food", "dining", "hotel").any { text.contains(it) } -> "Food"
        listOf("uber", "ola", "rapido", "metro", "fuel", "petrol", "diesel", "transport", "parking").any { text.contains(it) } -> "Transport"
        listOf("bigbasket", "dmart", "grocery", "groceries", "mart", "supermarket").any { text.contains(it) } -> "Groceries"
        listOf("electricity", "water", "gas", "mobile", "broadband", "internet", "recharge", "bill").any { text.contains(it) } -> "Utilities"
        listOf("amazon", "flipkart", "myntra", "shopping", "store").any { text.contains(it) } -> "Shopping"
        listOf("hospital", "clinic", "pharmacy", "medical", "health", "doctor").any { text.contains(it) } -> "Health"
        listOf("netflix", "prime", "hotstar", "movie", "cinema", "entertainment", "spotify").any { text.contains(it) } -> "Entertainment"
        listOf("ride", "cab", "taxi").any { text.contains(it) } -> "Rides"
        else -> "Other"
    }
}

fun parseExpenseFromSmsFallback(sender: String, body: String, dateInMillis: Long): Expense? =
    parseExpenseFromSms(sender, body, dateInMillis)

private fun legacyParseExpenseFromSmsFallback(sender: String, body: String, dateInMillis: Long): Expense? {
    val expenseKeywords = listOf("spent", "debited", "paid", "sent", "purchase", "txn", "transaction")
    val isLikelyExpense = expenseKeywords.any { body.contains(it, ignoreCase = true) }
    val isCreditOnly = body.contains("credited to your", ignoreCase = true) ||
        body.contains("has been credited", ignoreCase = true)

    if (!isLikelyExpense || isCreditOnly) return null

    val amountPatterns = listOf(
        Regex("(?i)(?:rs\\.?|inr|₹)\\s*([0-9,]+(?:\\.[0-9]+)?)"),
        Regex("(?i)(?:amount|amt)\\s*[:\\-]?\\s*([0-9,]+(?:\\.[0-9]+)?)")
    )
    val amount = amountPatterns
        .asSequence()
        .mapNotNull { regex -> regex.find(body)?.groupValues?.getOrNull(1) }
        .mapNotNull { it.replace(",", "").toDoubleOrNull() }
        .firstOrNull()
        ?: return null

    return Expense(
        amount = amount,
        currency = "INR",
        merchant = parseMerchantFromText(sender, body),
        category = "Auto-Parsed",
        dateInMillis = dateInMillis,
        originalSms = body
    )
}
