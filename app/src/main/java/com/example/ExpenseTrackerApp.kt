package com.example

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import com.example.ui.theme.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseTrackerApp(viewModel: MainViewModel = viewModel()) {
    val expenses by viewModel.uiState.collectAsStateWithLifecycle()
    val syncMessage by viewModel.syncMessage.collectAsStateWithLifecycle()
    val currentMode by viewModel.currentMode.collectAsStateWithLifecycle()
    val currentSpender by viewModel.currentSpender.collectAsStateWithLifecycle()
    val monthlyBudget by viewModel.monthlyBudget.collectAsStateWithLifecycle()
    val dailyPacingLimit by viewModel.dailyPacingLimit.collectAsStateWithLifecycle()
    val weeklyBudget by viewModel.weeklyBudget.collectAsStateWithLifecycle()
    val weekendAllowance by viewModel.weekendAllowance.collectAsStateWithLifecycle()
    val categoryBudgets by viewModel.categoryBudgets.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val navController = rememberNavController()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.READ_SMS] == true) {
            coroutineScope.launch {
                val smsMessages = readSms(context, limit = 50)
                viewModel.syncExpensesFromSms(smsMessages)
            }
        }
    }

    LaunchedEffect(Unit) {
        val readSmsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        val receiveSmsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        if (readSmsGranted && receiveSmsGranted) {
            coroutineScope.launch {
                val smsMessages = readSms(context, limit = 50)
                viewModel.syncExpensesFromSms(smsMessages)
            }
        }
    }

    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route

            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Outlined.AccountBalanceWallet, "Home") },
                    label = { Text("Home") },
                    selected = currentRoute == "home",
                    onClick = { navController.navigate("home") { launchSingleTop = true; popUpTo("home") } }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Outlined.Explore, "Radar") },
                    label = { Text("Spend Radar") },
                    selected = currentRoute == "radar",
                    onClick = { navController.navigate("radar") { launchSingleTop = true } }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Outlined.Sms, "SMS Sync") },
                    label = { Text("SMS Sync") },
                    selected = currentRoute == "sync",
                    onClick = { navController.navigate("sync") { launchSingleTop = true } }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Outlined.FavoriteBorder, "Planner") },
                    label = { Text("Planner") },
                    selected = currentRoute == "planner",
                    onClick = { navController.navigate("planner") { launchSingleTop = true } }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Outlined.Settings, "Settings") },
                    label = { Text("Settings") },
                    selected = currentRoute == "settings",
                    onClick = { navController.navigate("settings") { launchSingleTop = true } }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            composable("home") {
                HomeScreen(
                    expenses = expenses,
                    navController = navController,
                    onAddExpense = { viewModel.addExpense(it) },
                    monthlyBudget = monthlyBudget,
                    dailyPacingLimit = dailyPacingLimit,
                    weekendAllowance = weekendAllowance,
                    currentSpender = currentSpender,
                    onCurrentSpenderChange = { viewModel.setCurrentSpender(it) },
                    categoryBudgets = categoryBudgets
                )
            }
            
            composable("radar") {
                val customLimit by viewModel.customDailyLimit.collectAsStateWithLifecycle()
                SpendRadarScreen(
                    expenses = expenses,
                    mode = currentMode,
                    onModeSelect = { viewModel.setMode(it) },
                    customDailyLimit = customLimit,
                    onCustomLimitChange = { viewModel.setCustomDailyLimit(it) },
                    currentSpender = currentSpender,
                    onDeleteExpense = { viewModel.deleteExpense(it) }
                )
            }
            
            composable("sync") {
                SmsParserScreen(viewModel = viewModel)
            }
            
            composable("planner") {
                RidePlannerScreen(rideBudget = categoryBudgets["Rides"] ?: 0.0)
            }

            composable("settings") {
                val customLimit by viewModel.customDailyLimit.collectAsStateWithLifecycle()
                SettingsScreen(
                    mode = currentMode,
                    customDailyLimit = customLimit,
                    dailyPacingLimit = dailyPacingLimit,
                    monthlyBudget = monthlyBudget,
                    weeklyBudget = weeklyBudget,
                    weekendAllowance = weekendAllowance,
                    currentSpender = currentSpender,
                    totalTransactions = expenses.size,
                    syncMessage = syncMessage,
                    onModeSelect = { viewModel.setMode(it) },
                    onCustomLimitChange = { viewModel.setCustomDailyLimit(it) },
                    onDailyPacingChange = { viewModel.setDailyPacingLimit(it) },
                    onMonthlyBudgetChange = { viewModel.setMonthlyBudget(it) },
                    onWeeklyBudgetChange = { viewModel.setWeeklyBudget(it) },
                    onWeekendAllowanceChange = { viewModel.setWeekendAllowance(it) },
                    onCurrentSpenderChange = { viewModel.setCurrentSpender(it) },
                    onOpenSmsSync = { navController.navigate("sync") { launchSingleTop = true } },
                    onOpenMonthlyLedger = { navController.navigate("monthly_spend") { launchSingleTop = true } },
                    onOpenMonthlyBudget = { navController.navigate("monthly_budget") { launchSingleTop = true } }
                )
            }
            
            composable("weekly_dashboard") {
                WeeklyDashboardScreen(
                    expenses = expenses,
                    weeklyBudget = weeklyBudget,
                    onWeeklyBudgetChange = { viewModel.setWeeklyBudget(it) },
                    navController = navController
                )
            }

            composable("monthly_spend") {
                MonthlyTransactionsScreen(
                    expenses = expenses, 
                    navController = navController,
                    onDeleteExpense = { viewModel.deleteExpense(it) }
                )
            }

            composable("monthly_budget") {
                MonthlyBudgetScreen(
                    expenses = expenses,
                    monthlyBudget = monthlyBudget,
                    categoryBudgets = categoryBudgets,
                    onMonthlyBudgetChange = { viewModel.setMonthlyBudget(it) },
                    onCategoryBudgetChange = { category, budget -> viewModel.setCategoryBudget(category, budget) },
                    navController = navController
                )
            }

            composable("timeline") {
                TimelineScreen(expenses = expenses, onDeleteExpense = { viewModel.deleteExpense(it) })
            }
        }
    }
}




@Composable
fun AnalyticsScreen(expenses: List<Expense>) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Spending Analytics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        
        val totalSpent = expenses.sumOf { it.amount }
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Total Processed", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("₹${String.format("%.2f", totalSpent)}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }

        val byCategory = expenses.groupBy { it.category }.mapValues { it.value.sumOf { exp -> exp.amount } }.entries.sortedByDescending { it.value }
        if (byCategory.isNotEmpty()) {
            Text("Top Categories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(byCategory.toList()) { (cat, amount) ->
                    val progress = (amount / totalSpent.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(cat, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("₹${String.format("%.0f", amount)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TimelineScreen(expenses: List<Expense>, onDeleteExpense: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Transaction Timeline", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
        if (expenses.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No transactions found.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val sortedExpenses = expenses.sortedByDescending { it.dateInMillis }
                items(sortedExpenses, key = { it.id }) { expense ->
                    TimelineItem(expense, onDeleteExpense)
                }
            }
        }
    }
}

@Composable
fun TimelineItem(expense: Expense, onDelete: (Int) -> Unit) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Transaction?") },
            text = { Text("This transaction will be permanently removed.") },
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

fun formatDate(timeInMillis: Long): String {
    val formatter = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
    return formatter.format(java.util.Date(timeInMillis))
}

fun isToday(timeInMillis: Long): Boolean {
    val today = Calendar.getInstance()
    val date = Calendar.getInstance().apply { time = Date(timeInMillis) }
    return today.get(Calendar.YEAR) == date.get(Calendar.YEAR) &&
           today.get(Calendar.DAY_OF_YEAR) == date.get(Calendar.DAY_OF_YEAR)
}

fun getCategoryIcon(category: String) = when (category.lowercase(Locale.ROOT)) {
    "food", "dining" -> Icons.Filled.Restaurant
    "groceries" -> Icons.Filled.ShoppingCart
    "entertainment", "movie" -> Icons.Filled.PlayArrow
    "health" -> Icons.Filled.Favorite
    "transport" -> Icons.Filled.DirectionsCar
    "utilities" -> Icons.Filled.Bolt
    else -> Icons.Filled.Warning
}
