package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import com.example.ui.theme.*
import java.util.Calendar
import java.util.Locale

private enum class SmsPeriodFilter(val label: String) {
    TODAY("Today"),
    WEEK("This Week"),
    MONTH("This Month"),
    ALL("All")
}

private enum class SmsSortOption(val label: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
    HIGH_AMOUNT("High Amount"),
    LOW_AMOUNT("Low Amount"),
    MERCHANT("Merchant")
}

@Composable
fun SmsParserScreen(viewModel: MainViewModel) {
    val expenses by viewModel.uiState.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val syncMessage by viewModel.syncMessage.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var scanDays by remember { mutableStateOf(7) }
    var searchQuery by remember { mutableStateOf("") }
    var periodFilter by remember { mutableStateOf(SmsPeriodFilter.ALL) }
    var sortOption by remember { mutableStateOf(SmsSortOption.NEWEST) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.READ_SMS] == true) {
            coroutineScope.launch {
                val smsMessages = readSms(context, limit = 1000, daysAgo = scanDays)
                viewModel.syncExpensesFromSms(smsMessages)
            }
        }
    }

    val parsedData = remember(expenses) {
        val parsedExpenses = expenses.filter { it.isSmsTransaction() }
        val totalAmount = parsedExpenses.sumOf { it.amount }
        Pair(parsedExpenses, totalAmount)
    }
    val parsedExpenses = parsedData.first
    val totalAmount = parsedData.second
    val filteredExpenses = remember(parsedExpenses, searchQuery, periodFilter, sortOption) {
        val periodExpenses = parsedExpenses.filterByPeriod(periodFilter)
        val searchedExpenses = if (searchQuery.isBlank()) {
            periodExpenses
        } else {
            periodExpenses.filter { expense ->
                expense.originalSms.contains(searchQuery, ignoreCase = true) ||
                    expense.merchant.contains(searchQuery, ignoreCase = true) ||
                    expense.category.contains(searchQuery, ignoreCase = true) ||
                    expense.amount.toString().contains(searchQuery, ignoreCase = true)
            }
        }
        searchedExpenses.sortedByOption(sortOption)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .background(MaterialTheme.colorScheme.background)
    ) {

        Text("SMS Dashboard", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground)
        Text("Total expenses read from SMS: ${parsedExpenses.size} (₹${String.format("%.0f", totalAmount)})", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (syncMessage != null) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Text(
                    text = syncMessage!!,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.Message, contentDescription = "SMS", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("SMS AUTO-TRACKING", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("When a bank SMS arrives, TwoFunds parses matching transaction text locally and refreshes your balances.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        Text("LEDGER INGESTION TOOLS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp)
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    scanDays = 7
                    val hasRead = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
                    val hasReceive = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
                    if (hasRead && hasReceive) {
                        coroutineScope.launch {
                            val smsMessages = readSms(context, limit = 1000, daysAgo = 7)
                            viewModel.syncExpensesFromSms(smsMessages)
                        }
                    } else {
                        permissionLauncher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS))
                    }
                }, 
                modifier = Modifier.weight(1f).height(56.dp), 
                shape = RoundedCornerShape(16.dp), 
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                if (isSyncing && scanDays == 7) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Last 7 Days")
            }
            Button(
                onClick = {
                    scanDays = 30
                    val hasRead = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
                    val hasReceive = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
                    if (hasRead && hasReceive) {
                        coroutineScope.launch {
                            val smsMessages = readSms(context, limit = 1000, daysAgo = 30)
                            viewModel.syncExpensesFromSms(smsMessages)
                        }
                    } else {
                        permissionLauncher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS))
                    }
                }, 
                modifier = Modifier.weight(1f).height(56.dp), 
                shape = RoundedCornerShape(16.dp), 
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary
                )
            ) {
                if (isSyncing && scanDays == 30) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onTertiary, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Last 30 Days")
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search transaction message, merchant, category, or amount") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text("SHOW", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmsPeriodFilter.entries.forEach { option ->
                FilterChip(
                    selected = periodFilter == option,
                    onClick = { periodFilter = option },
                    label = { Text(option.label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("SORT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmsSortOption.entries.forEach { option ->
                FilterChip(
                    selected = sortOption == option,
                    onClick = { sortOption = option },
                    label = { Text(option.label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("${periodFilter.label.uppercase(Locale.getDefault())} TRANSACTION SMS (${filteredExpenses.size} items)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp)
        Spacer(modifier = Modifier.height(16.dp))
        
        if (filteredExpenses.isEmpty()) {
            Text(
                if (searchQuery.isBlank()) "No parsed transactions found yet. Tap scan above." else "No transaction message found for \"$searchQuery\".",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            filteredExpenses.take(30).forEach { expense ->
                SmsPreviewCard(expense, onDelete = { viewModel.deleteExpense(it) })
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
        
        Spacer(modifier = Modifier.height(64.dp))
    }
}

private fun List<Expense>.filterByPeriod(periodFilter: SmsPeriodFilter): List<Expense> {
    val now = Calendar.getInstance()
    val startMillis = when (periodFilter) {
        SmsPeriodFilter.TODAY -> now.startOfDayMillis()
        SmsPeriodFilter.WEEK -> now.startOfWeekMillis()
        SmsPeriodFilter.MONTH -> now.startOfMonthMillis()
        SmsPeriodFilter.ALL -> Long.MIN_VALUE
    }
    return filter { it.dateInMillis >= startMillis }
}

private fun List<Expense>.sortedByOption(sortOption: SmsSortOption): List<Expense> =
    when (sortOption) {
        SmsSortOption.NEWEST -> sortedByDescending { it.dateInMillis }
        SmsSortOption.OLDEST -> sortedBy { it.dateInMillis }
        SmsSortOption.HIGH_AMOUNT -> sortedByDescending { it.amount }
        SmsSortOption.LOW_AMOUNT -> sortedBy { it.amount }
        SmsSortOption.MERCHANT -> sortedWith(compareBy<Expense, String>(String.CASE_INSENSITIVE_ORDER) { it.merchant })
    }

private fun Calendar.startOfDayMillis(): Long =
    cloneCalendar().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun Calendar.startOfWeekMillis(): Long =
    cloneCalendar().apply {
        firstDayOfWeek = Calendar.MONDAY
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun Calendar.startOfMonthMillis(): Long =
    cloneCalendar().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun Calendar.cloneCalendar(): Calendar =
    (clone() as Calendar)

@Composable
fun SmsPreviewCard(expense: Expense, onDelete: (Int) -> Unit) {
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
