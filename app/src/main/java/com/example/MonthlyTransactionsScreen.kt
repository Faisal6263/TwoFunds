package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.filled.Delete
import androidx.navigation.NavController
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyTransactionsScreen(budgetSummary: BudgetSummary, navController: NavController, onDeleteExpense: (Int) -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedDayLabel by remember(budgetSummary.monthName) { mutableStateOf<String?>(null) }
    val monthlyExpenses = budgetSummary.monthlyExpenses

    val filteredExpenses = remember(monthlyExpenses, searchQuery) {
        if (searchQuery.isBlank()) {
            monthlyExpenses
        } else {
            monthlyExpenses.filter {
                it.merchant.contains(searchQuery, ignoreCase = true) ||
                it.category.contains(searchQuery, ignoreCase = true) ||
                it.originalSms.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val totalSpent = filteredExpenses.filter { it.isDebit }.totalAmount()
    val totalCredited = filteredExpenses.filter { it.isCredit }.totalAmount()
    val netCashFlow = roundMoney(totalCredited - totalSpent)
    val unassignedTotal = filteredExpenses.filter { it.isDebit && SpenderProfile.entries.none(it::matchesProfile) }.totalAmount()
    val husbandTotal = filteredExpenses.filter { it.isDebit && it.matchesProfile(SpenderProfile.HUSBAND) }.totalAmount()
    val wifeTotal = filteredExpenses.filter { it.isDebit && it.matchesProfile(SpenderProfile.WIFE) }.totalAmount()
    val sharedTotal = filteredExpenses.filter { it.isDebit && it.matchesProfile(SpenderProfile.SHARED) }.totalAmount()
    val currentMonthName = budgetSummary.monthName

    val monthlyDayCards = remember(filteredExpenses, currentMonthName, budgetSummary.asOfMillis) {
        val currentMonth = Calendar.getInstance().apply {
            timeInMillis = budgetSummary.asOfMillis
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val daysInMonth = currentMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
        val dayLabelFormatter = SimpleDateFormat("EEE • dd MMM", Locale.getDefault())

        (1..daysInMonth).map { dayOfMonth ->
            val dayCalendar = (currentMonth.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, dayOfMonth)
            }
            val dayTransactions = filteredExpenses.filter { expense ->
                val expenseCalendar = Calendar.getInstance().apply {
                    timeInMillis = expense.dateInMillis
                }
                expenseCalendar.get(Calendar.YEAR) == dayCalendar.get(Calendar.YEAR) &&
                    expenseCalendar.get(Calendar.MONTH) == dayCalendar.get(Calendar.MONTH) &&
                    expenseCalendar.get(Calendar.DAY_OF_MONTH) == dayOfMonth
            }.sortedByDescending { it.dateInMillis }

            MonthlyDayData(
                dayLabel = dayLabelFormatter.format(dayCalendar.time),
                spent = dayTransactions.filter { it.isDebit }.totalAmount(),
                credited = dayTransactions.filter { it.isCredit }.totalAmount(),
                transactions = dayTransactions
            )
        }
    }

    monthlyDayCards.firstOrNull { it.dayLabel == selectedDayLabel }?.let { day ->
        AlertDialog(
            onDismissRequest = { selectedDayLabel = null },
            title = {
                Column {
                    Text(
                        text = day.dayLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "${day.transactions.size} transactions • Rs.${formatMoney(day.spent)} spent • Rs.${formatMoney(day.credited)} credited",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            },
            text = {
                if (day.transactions.isEmpty()) {
                    Text(
                        text = "No transactions found for this day.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        day.transactions.forEach { expense ->
                            DetailedExpenseCard(expense = expense)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedDayLabel = null }) {
                    Text("Close", color = PrimaryColor)
                }
            },
            containerColor = BgColor,
            shape = RoundedCornerShape(24.dp)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Monthly Transactions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = currentMonthName,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BgColor
                )
            )
        },
        containerColor = BgColor
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("monthly-ledger")
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp)
        ) {
            // Search Input
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().testTag("monthly-search"),
                    placeholder = { Text("Search by merchant or category...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondary)
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryColor,
                        unfocusedBorderColor = CardBorder,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    singleLine = true
                )
            }

            // Summary Card
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = if (searchQuery.isBlank()) "TOTAL SPENT THIS MONTH" else "MATCHING MONTHLY SPEND",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.7f),
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "₹${formatMoney(totalSpent)}",
                            modifier = Modifier.testTag("monthly-spent"),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("CREDITED", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                                Text("+₹${formatMoney(totalCredited)}", modifier = Modifier.testTag("monthly-credit"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFFBBF7D0))
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("NET CASH FLOW", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                                Text("${if (netCashFlow >= 0) "+" else "-"}₹${formatMoney(kotlin.math.abs(netCashFlow))}", modifier = Modifier.testTag("monthly-net"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Based on ${filteredExpenses.size} matching recorded transactions",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                    border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        MonthlyProfileTotal("👨 Husband", husbandTotal, Modifier.weight(1f))
                        MonthlyProfileTotal("👩 Wife", wifeTotal, Modifier.weight(1f))
                        MonthlyProfileTotal("🤝 Shared", sharedTotal, Modifier.weight(1f))
                    }
                    if (unassignedTotal > 0) Text("Unassigned: ₹${formatMoney(unassignedTotal)}", modifier = Modifier.padding(16.dp), color = TextSecondary)
                }
            }

            item {
                Column {
                    Text(
                        text = "CALENDAR TYPE MONTHLY GRID",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        text = "${monthlyDayCards.size} days • Tap a day to view transactions",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )

                    monthlyDayCards.chunked(2).forEach { rowDays ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowDays.forEach { day ->
                                MonthlyDayCard(
                                    day = day,
                                    modifier = Modifier.weight(1f),
                                    onClick = { selectedDayLabel = day.dayLabel }
                                )
                            }
                            if (rowDays.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // Category Breakdown Panel
            if (filteredExpenses.any { it.isDebit }) {
                val byCategory = filteredExpenses.filter { it.isDebit }.groupBy { normalizeBudgetCategory(it.category) }
                    .mapValues { it.value.totalAmount() }
                    .entries.sortedByDescending { it.value }

                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "CATEGORY BREAKDOWN",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            byCategory.forEach { (category, amount) ->
                                val progress = categoryShare(amount, totalSpent)
                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(category, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                        Text("₹${formatMoney(amount)} (${utilizationLabel(amount, totalSpent)})", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = PrimaryColor)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = progress,
                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                        color = PrimaryColor,
                                        trackColor = CardBorder
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "TRANSACTIONS LEDGER (${filteredExpenses.size})",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
            }

            if (filteredExpenses.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = if (searchQuery.isNotEmpty()) "No match for \"$searchQuery\"" else "No transactions recorded for this month.",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            } else {
                items(filteredExpenses, key = { it.id }) { expense ->
                    TransactionRowItem(expense, onDeleteExpense)
                }
            }
        }
    }
}

private data class MonthlyDayData(
    val dayLabel: String,
    val spent: Double,
    val credited: Double,
    val transactions: List<Expense>
)

@Composable
private fun MonthlyDayCard(
    day: MonthlyDayData,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isEmpty = day.transactions.isEmpty()
    val isCreditOnly = day.spent == 0.0 && day.credited > 0.0

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isEmpty) MaterialTheme.colorScheme.surface else Color(0xFFF0FDF4)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isEmpty) CardBorder else SuccessGreen.copy(alpha = 0.5f)
        ),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = day.dayLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "SPENT",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 8.sp,
                color = TextSecondary,
                letterSpacing = 0.5.sp
            )
            Text(
                text = "₹${formatMoney(day.spent)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = if (isEmpty || isCreditOnly) TextPrimary else SuccessGreen
            )
            Text(
                text = "+₹${formatMoney(day.credited)} credited",
                style = MaterialTheme.typography.labelSmall,
                color = SuccessGreen,
                fontSize = 10.sp
            )
            Text(
                text = "${day.transactions.size} transactions",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontSize = 10.sp
            )

            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isEmpty) Color(0xFFF3F4F6) else Color(0xFFDCFCE7),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = if (isEmpty) "No Transactions" else if (isCreditOnly) "Credits Recorded 💰" else "Transactions Logged 🌿",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isEmpty) TextSecondary else SuccessGreen,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 10.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tap to view transactions",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = PrimaryColor,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun MonthlyProfileTotal(label: String, amount: Double, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextSecondary)
        Spacer(modifier = Modifier.height(4.dp))
        Text("₹${formatMoney(amount)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = TextPrimary)
    }
}

@Composable
fun TransactionRowItem(expense: Expense, onDelete: (Int) -> Unit) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Transaction?") },
            text = { Text("This transaction will be permanently removed and all balances will be recalculated.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete(expense.id)
                }) {
                    Text("Delete", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    DetailedExpenseCard(expense = expense, onDelete = { showDeleteDialog = true })
}
