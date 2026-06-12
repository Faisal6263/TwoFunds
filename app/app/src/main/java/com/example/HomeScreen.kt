package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CalendarToday
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

private class HomeData(
    val todayTotal: Double,
    val monthlyExpenses: List<Expense>,
    val monthlyTotal: Double,
    val savedSoFar: Double,
    val todayLeft: Double,
    val totalWeekSpent: Double,
    val displayExpenses: List<Expense>
)

@Composable
fun HomeScreen(
    expenses: List<Expense>,
    budgetSummary: BudgetSummary,
    navController: NavController,
    onAddExpense: (Expense) -> Unit,
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

    val homeData = remember(expenses, budgetSummary) {
        val demoExpenses = listOf(
            Expense(amount = 540.0, category = "Food", currency = "₹", merchant = "Zomato Chicken Roll", dateInMillis = System.currentTimeMillis(), originalSms = ""),
            Expense(amount = 1250.0, category = "Petrol", currency = "₹", merchant = "HPCL Fuel Station", dateInMillis = System.currentTimeMillis(), originalSms = ""),
            Expense(amount = 1800.0, category = "Utility Bills", currency = "₹", merchant = "Electricity Bill Payment", dateInMillis = System.currentTimeMillis(), originalSms = ""),
            Expense(amount = 1200.0, category = "Shopping", currency = "₹", merchant = "Zara Summer Wear", dateInMillis = System.currentTimeMillis(), originalSms = "")
        )
        val displayExpenses = if (expenses.isNotEmpty()) expenses.take(5) else demoExpenses

        HomeData(
            todayTotal = budgetSummary.todayTotal,
            monthlyExpenses = budgetSummary.monthlyExpenses,
            monthlyTotal = budgetSummary.monthlyTotal,
            savedSoFar = budgetSummary.monthlyRemaining,
            todayLeft = budgetSummary.todayRemaining,
            totalWeekSpent = budgetSummary.weekTotal,
            displayExpenses = displayExpenses
        )
    }

    val todayLeft = homeData.todayLeft
    val savedSoFar = homeData.savedSoFar
    val monthlyExpenses = homeData.monthlyExpenses
    val todayTotal = homeData.todayTotal
    val totalWeekSpent = homeData.totalWeekSpent
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

        Text(
            text = "BUDGET WINDOWS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BudgetWindowCard(
                title = "Daily Spend",
                amount = todayTotal,
                helper = "Live radar",
                icon = Icons.Outlined.CalendarToday,
                tint = ErrorRed,
                modifier = Modifier.weight(1f),
                onClick = { navController.navigate("radar") }
            )
            BudgetWindowCard(
                title = "Daily Remaining",
                amount = todayLeft,
                helper = "Live radar",
                icon = Icons.Outlined.CalendarToday,
                tint = SuccessGreen,
                modifier = Modifier.weight(1f),
                onClick = { navController.navigate("radar") }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BudgetWindowCard(
                title = "Weekly Spend",
                amount = totalWeekSpent,
                helper = "${budgetSummary.weekExpenses.size} items",
                icon = Icons.Outlined.AccountBalanceWallet,
                tint = ErrorRed,
                modifier = Modifier.weight(1f),
                onClick = { navController.navigate("weekly_dashboard") }
            )
            BudgetWindowCard(
                title = "Weekly Remaining",
                amount = budgetSummary.weekRemaining,
                helper = "Calendar grid",
                icon = Icons.Outlined.AccountBalanceWallet,
                tint = PrimaryColor,
                modifier = Modifier.weight(1f),
                onClick = { navController.navigate("weekly_dashboard") }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BudgetWindowCard(
                title = "Monthly Spend",
                amount = monthlyTotal,
                helper = "${monthlyExpenses.size} items",
                icon = Icons.Outlined.CalendarMonth,
                tint = ErrorRed,
                modifier = Modifier.weight(1f),
                onClick = { navController.navigate("monthly_spend") }
            )
            BudgetWindowCard(
                title = "Monthly Remaining",
                amount = savedSoFar,
                helper = budgetSummary.monthName,
                icon = Icons.Outlined.CalendarMonth,
                tint = PrimaryColor,
                modifier = Modifier.weight(1f),
                onClick = { navController.navigate("monthly_spend") }
            )
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
            displayExpenses.forEach { exp ->
                HomeTransactionItem(exp)
            }
        }
        
        Spacer(modifier = Modifier.height(64.dp))
    }
}

@Composable
private fun BudgetWindowCard(
    title: String,
    amount: Double,
    helper: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .heightIn(min = 132.dp)
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(tint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "₹${String.format("%,.0f", amount)}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = tint
            )
            Text(helper, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
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
