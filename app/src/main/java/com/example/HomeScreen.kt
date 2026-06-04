package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.TwoWheeler
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.ui.theme.*
import java.util.*

private class HomeData(
    val todayExpenses: List<Expense>,
    val todayTotal: Double,
    val monthlyExpenses: List<Expense>,
    val monthlyTotal: Double,
    val savedSoFar: Double,
    val todayLeft: Double,
    val dailyBudget: Double,
    val thisWeekExpenses: List<Expense>,
    val dailySpentMap: Map<Int, Double>,
    val accumulatedSavings: Double,
    val currentWeekendBalance: Double,
    val totalWeekSpent: Double,
    val displayExpenses: List<Expense>
)

@Composable
fun HomeScreen(
    expenses: List<Expense>,
    budgetSummary: BudgetSummary,
    navController: NavController,
    onAddExpense: (Expense) -> Unit,
    monthlyBudget: Double,
    dailyPacingLimit: Double,
    weekendAllowance: Double,
    currentSpender: SpenderProfile,
    onCurrentSpenderChange: (SpenderProfile) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }

    if (showAddDialog) {
        var merchant by remember { mutableStateOf("") }
        var amountStr by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("Food") }
        val categories = listOf("Food", "Transport", "Groceries", "Utilities", "Shopping", "Health", "Entertainment", "Other")
        val amount = amountStr.toDoubleOrNull()
        val canSave = merchant.isNotBlank() && amount != null && amount > 0
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Expense") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = merchant, onValueChange = { merchant = it }, label = { Text("Merchant") }, singleLine = true)
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it.filter { char -> char.isDigit() || char == '.' } },
                        label = { Text("Amount") },
                        prefix = { Text("Rs.") },
                        singleLine = true,
                        isError = amountStr.isNotBlank() && (amount == null || amount <= 0)
                    )
                    Text("Category", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    categories.chunked(4).forEach { rowCategories ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowCategories.forEach { option ->
                                FilterChip(
                                    selected = category == option,
                                    onClick = { category = option },
                                    label = { Text(option) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(enabled = canSave, onClick = {
                    val parsedAmount = amount ?: return@Button
                    if (merchant.isNotBlank() && parsedAmount > 0) {
                        onAddExpense(Expense(amount = parsedAmount, currency = "INR", merchant = merchant.trim(), category = category, dateInMillis = System.currentTimeMillis(), originalSms = "manual-" + java.util.UUID.randomUUID().toString()))
                        showAddDialog = false
                    }
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }

    val homeData = remember(expenses, monthlyBudget, dailyPacingLimit, weekendAllowance, budgetSummary) {
        val todayExpenses = expenses.filter { isToday(it.dateInMillis) }
        val todayTotal = todayExpenses.sumOf { it.amount }
        
        // Calculate Monthly using timestamp bounds to avoid instantiating Calendar objects in a loop
        val calendar = Calendar.getInstance()
        val startOfMonthCal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfMonthMillis = startOfMonthCal.timeInMillis
        startOfMonthCal.add(Calendar.MONTH, 1)
        val endOfMonthMillis = startOfMonthCal.timeInMillis - 1
        
        val monthlyExpenses = expenses.filter { 
            it.dateInMillis in startOfMonthMillis..endOfMonthMillis
        }
        val monthlyTotal = monthlyExpenses.sumOf { it.amount }
        val savedSoFar = (monthlyBudget - monthlyTotal).coerceAtLeast(0.0)
        
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val dailyBudget = monthlyBudget / daysInMonth
        val todayLeft = (dailyBudget - todayTotal).coerceAtLeast(0.0)

        // --- WEEKEND ROLLOVER & SMART PACING WINDOW ---
        val nowCal = Calendar.getInstance()
        val currentWeekYear = nowCal.get(Calendar.WEEK_OF_YEAR)
        val currentYearNo = nowCal.get(Calendar.YEAR)

        // Filter current week's expenses (Monday to Sunday)
        val thisWeekExpenses = expenses.filter {
            val expCal = Calendar.getInstance().apply { timeInMillis = it.dateInMillis }
            expCal.get(Calendar.WEEK_OF_YEAR) == currentWeekYear && expCal.get(Calendar.YEAR) == currentYearNo
        }

        // Initialize spent arrays
        val dailySpentMap = mutableMapOf<Int, Double>().apply {
            for (i in 1..7) put(i, 0.0)
        }
        for (exp in thisWeekExpenses) {
            val expCal = Calendar.getInstance().apply { timeInMillis = exp.dateInMillis }
            val day = expCal.get(Calendar.DAY_OF_WEEK)
            dailySpentMap[day] = (dailySpentMap[day] ?: 0.0) + exp.amount
        }

        val todayDayOfWeekIndex = nowCal.get(Calendar.DAY_OF_WEEK)
        val weekdaysList = listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)

        var accumulatedSavings = 0.0
        val weekdayPacingLimit = dailyPacingLimit

        for (day in weekdaysList) {
            val hasDayStarted = when {
                todayDayOfWeekIndex == Calendar.SUNDAY || todayDayOfWeekIndex == Calendar.SATURDAY -> true
                else -> day <= todayDayOfWeekIndex
            }
            if (hasDayStarted) {
                val spent = dailySpentMap[day] ?: 0.0
                val saving = (weekdayPacingLimit - spent).coerceAtLeast(0.0)
                accumulatedSavings += saving
            }
        }

        val baseWeekendAllowance = weekendAllowance
        val currentWeekendBalance = baseWeekendAllowance + accumulatedSavings
        val totalWeekSpent = thisWeekExpenses.sumOf { it.amount }

        val demoExpenses = listOf(
            Expense(amount = 540.0, category = "Food", currency = "₹", merchant = "Zomato Chicken Roll", dateInMillis = System.currentTimeMillis(), originalSms = ""),
            Expense(amount = 1250.0, category = "Petrol", currency = "₹", merchant = "HPCL Fuel Station", dateInMillis = System.currentTimeMillis(), originalSms = ""),
            Expense(amount = 1800.0, category = "Utility Bills", currency = "₹", merchant = "Electricity Bill Payment", dateInMillis = System.currentTimeMillis(), originalSms = ""),
            Expense(amount = 1200.0, category = "Shopping", currency = "₹", merchant = "Zara Summer Wear", dateInMillis = System.currentTimeMillis(), originalSms = "")
        )
        val displayExpenses = if (expenses.isNotEmpty()) expenses.take(5) else demoExpenses

        HomeData(
            todayExpenses = budgetSummary.todayExpenses,
            todayTotal = budgetSummary.todayTotal,
            monthlyExpenses = budgetSummary.monthlyExpenses,
            monthlyTotal = budgetSummary.monthlyTotal,
            savedSoFar = budgetSummary.monthlyRemaining,
            todayLeft = budgetSummary.todayRemaining,
            dailyBudget = budgetSummary.activeDailyLimit,
            thisWeekExpenses = budgetSummary.weekExpenses,
            dailySpentMap = budgetSummary.dailySpentByCalendarDay,
            accumulatedSavings = budgetSummary.weekdaySavings,
            currentWeekendBalance = budgetSummary.weekendFundBalance,
            totalWeekSpent = budgetSummary.weekTotal,
            displayExpenses = displayExpenses
        )
    }

    val todayLeft = homeData.todayLeft
    val dailyBudget = homeData.dailyBudget
    val savedSoFar = homeData.savedSoFar
    val monthlyExpenses = homeData.monthlyExpenses
    val todayTotal = homeData.todayTotal
    val totalWeekSpent = homeData.totalWeekSpent
    val currentWeekendBalance = homeData.currentWeekendBalance
    val accumulatedSavings = homeData.accumulatedSavings
    val monthlyTotal = homeData.monthlyTotal
    val displayExpenses = homeData.displayExpenses
    val husbandMonthlyTotal = budgetSummary.monthlyProfileTotals[SpenderProfile.HUSBAND] ?: 0.0
    val wifeMonthlyTotal = budgetSummary.monthlyProfileTotals[SpenderProfile.WIFE] ?: 0.0
    val sharedMonthlyTotal = budgetSummary.monthlyProfileTotals[SpenderProfile.SHARED] ?: 0.0
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .background(MaterialTheme.colorScheme.background)
    ) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
            border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Who is spending right now? ✨", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("New manual and SMS expenses will be tagged to this profile.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    Text(currentSpender.emoji, fontSize = 24.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    SpenderProfile.entries.forEach { profile ->
                        FilterChip(
                            selected = currentSpender == profile,
                            onClick = { onCurrentSpenderChange(profile) },
                            label = { Text("${profile.emoji} ${profile.displayName}") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryColor,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 1. DYNAMIC BUDGET PACING
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.5.dp, PrimaryColor),
            modifier = Modifier.fillMaxWidth().clickable { navController.navigate("weekly_dashboard") }
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(32.dp).clip(CircleShape).background(PrimaryContainerColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("💵", fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("DYNAMIC BUDGET PACING", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = PrimaryColor, letterSpacing = 1.sp)
                        Text("Configured on ₹${String.format("%,.0f", monthlyBudget)}/mo plan", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("DAILY PACING", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("₹${String.format("%.0f", todayTotal)} / ₹${String.format("%.0f", homeData.dailyBudget)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = TextPrimary)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("WEEKLY SPENT", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("₹${String.format("%.0f", totalWeekSpent)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = TextPrimary)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = CardBorder)

                // Highlighted Weekend Rollover Segment
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF0FDF4))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🐷", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("WEEKEND FUND BALANCE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = SuccessGreen)
                            }
                            Text("ACTIVE ⚡", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = SuccessGreen)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "₹${String.format("%,.0f", currentWeekendBalance)}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = SuccessGreen
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Base allowance (₹${String.format("%.0f", weekendAllowance)}) + Weekday Roll: ₹${String.format("%.0f", accumulatedSavings)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF166534),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "View Weekly Grid & Detail",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = PrimaryColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // 2. LIVE SPENDING RADAR
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)), // Deep navy
            modifier = Modifier.fillMaxWidth().clickable { navController.navigate("radar") }
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Explore, contentDescription = null, tint = PrimaryColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("LIVE SPENDING RADAR", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = PrimaryColor, letterSpacing = 1.sp)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Where & How Can I Spend Today? →", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                    Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF1F2937)), contentAlignment = Alignment.Center) {
                         Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Dynamically computed category guidelines based only on parsed SMS transaction messages.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF9CA3AF))
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // 3. MONTHLY SPEND AND REMAINING BOXES
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Monthly Spend
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .weight(1f)
                    .clickable { navController.navigate("monthly_spend") }
            ) {
                Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Monthly Spend", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("₹${String.format("%.0f", monthlyTotal)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ErrorRed)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("${monthlyExpenses.size} items", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // Monthly Remaining
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Monthly Remaining", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("₹${String.format("%.0f", savedSoFar)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = PrimaryColor)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Remaining budget", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
            border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                ProfileSpendMini("👨 Husband", husbandMonthlyTotal, Modifier.weight(1f))
                ProfileSpendMini("👩 Wife", wifeMonthlyTotal, Modifier.weight(1f))
                ProfileSpendMini("🤝 Shared", sharedMonthlyTotal, Modifier.weight(1f))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        // 4. Action Buttons
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionButton("Ride Planner", Icons.Outlined.TwoWheeler, PrimaryColor, Modifier.weight(1f)) { navController.navigate("planner") }
            ActionButton("Add Expense", Icons.Default.Add, SuccessGreen, Modifier.weight(1f)) { showAddDialog = true }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionButton("SMS Ingest", Icons.Outlined.Sms, SuccessGreen, Modifier.weight(1f)) { navController.navigate("sync") }
            Spacer(modifier = Modifier.weight(1f))
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Local spending insight
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(modifier = Modifier.fillMaxHeight().width(4.dp).background(PrimaryColor, RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)))
            Card(
                shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp, topStart = 0.dp, bottomStart = 0.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("LOCAL SPENDING INSIGHT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = PrimaryColor, letterSpacing = 2.sp)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("\"Budget is in pristine shape! Daily limits remain safe for weekend rides.\"", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { navController.navigate("weekly_dashboard") }) {
                        Text("Explore your Weekly Dashboard & Grid", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Recent Family Transactions
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("RECENT FAMILY TRANSACTIONS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
            Text("View all ledger analytics", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = PrimaryColor, modifier = Modifier.clickable { navController.navigate("timeline") })
        }
        Spacer(modifier = Modifier.height(12.dp))
        
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val demoExpenses = listOf(
                Expense(amount = 540.0, category = "Food", currency = "INR", merchant = "Zomato Chicken Roll", dateInMillis = System.currentTimeMillis(), originalSms = ""),
                Expense(amount = 1250.0, category = "Petrol", currency = "INR", merchant = "HPCL Fuel Station", dateInMillis = System.currentTimeMillis(), originalSms = ""),
                Expense(amount = 1800.0, category = "Utility Bills", currency = "INR", merchant = "Electricity Bill Payment", dateInMillis = System.currentTimeMillis(), originalSms = ""),
                Expense(amount = 1200.0, category = "Shopping", currency = "INR", merchant = "Zara Summer Wear", dateInMillis = System.currentTimeMillis(), originalSms = "")
            )
            val displayExpenses = if (expenses.isNotEmpty()) expenses.take(5) else demoExpenses

            displayExpenses.forEach { exp ->
                HomeTransactionItem(exp)
            }
        }
        
        Spacer(modifier = Modifier.height(64.dp))
    }
}

@Composable
private fun ProfileSpendMini(label: String, amount: Double, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextSecondary)
        Spacer(modifier = Modifier.height(4.dp))
        Text("₹${String.format("%,.0f", amount)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = TextPrimary)
    }
}

@Composable
fun ActionButton(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(vertical = 20.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = title, tint = tint, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = if (tint == SuccessGreen) Color(0xFF065F46) else PrimaryColor)
        }
    }
}

@Composable
fun HomeTransactionItem(expense: Expense) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            DetailedExpenseCard(expense = expense)
        }
    }
}
