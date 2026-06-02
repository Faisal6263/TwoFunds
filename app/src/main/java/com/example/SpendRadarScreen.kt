package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

private class RadarData(
    val actualDailyLimit: Double,
    val todayExpenses: List<Expense>,
    val todayTotal: Double,
    val remaining: Double,
    val progress: Float,
    val todayDateStr: String
)

@Composable
fun SpendRadarScreen(
    expenses: List<Expense>,
    mode: SpendMode,
    onModeSelect: (SpendMode) -> Unit,
    customDailyLimit: Double,
    onCustomLimitChange: (Double) -> Unit,
    currentSpender: SpenderProfile,
    onCurrentSpenderChange: (SpenderProfile) -> Unit,
    onDeleteExpense: (Int) -> Unit
) {
    val scrollState = rememberScrollState()
    var showCustomLimitDialog by remember { mutableStateOf(false) }
    var viewProfile by remember { mutableStateOf<SpenderProfile?>(null) }
    
    val radarData = remember(expenses, mode, customDailyLimit, viewProfile) {
        val actualDailyLimit = when(mode) {
            SpendMode.FRUGAL -> 400.0
            SpendMode.STANDARD -> 800.0
            SpendMode.SPLURGE -> 1500.0
            SpendMode.CUSTOM -> customDailyLimit
        }
        val todayExpenses = expenses.filter {
            isToday(it.dateInMillis) && (viewProfile == null || it.spentBy == viewProfile?.displayName)
        }
        val todayTotal = todayExpenses.sumOf { it.amount }
        val remaining = (actualDailyLimit - todayTotal).coerceAtLeast(0.0)
        val progress = if (actualDailyLimit > 0) (todayTotal / actualDailyLimit).toFloat().coerceIn(0f, 1f) else 0f
        
        val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())
        val todayDateStr = dateFormat.format(Date())
        
        RadarData(actualDailyLimit, todayExpenses, todayTotal, remaining, progress, todayDateStr)
    }

    val actualDailyLimit = radarData.actualDailyLimit
    val todayExpenses = radarData.todayExpenses
    val todayTotal = radarData.todayTotal
    val remaining = radarData.remaining
    val progress = radarData.progress
    val todayDateStr = radarData.todayDateStr
    
    var expenseToDelete by remember { mutableStateOf<Int?>(null) }

    if (expenseToDelete != null) {
        AlertDialog(
            onDismissRequest = { expenseToDelete = null },
            title = { Text("Delete Transaction?") },
            text = { Text("This transaction will be permanently removed.") },
            confirmButton = {
                TextButton(onClick = {
                    expenseToDelete?.let { onDeleteExpense(it) }
                    expenseToDelete = null
                }) {
                    Text("Delete", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { expenseToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // App Header section
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(PrimaryColor))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Viewing: ${viewProfile?.displayName ?: "All profiles"}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFFFBEB),
                    border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.35f))
                ) {
                    Text("${currentSpender.emoji} Assigning to ${currentSpender.displayName}", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                }
            }
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = viewProfile == null,
                    onClick = { viewProfile = null },
                    label = { Text("All") }
                )
                SpenderProfile.entries.forEach { profile ->
                    FilterChip(
                        selected = viewProfile == profile,
                        onClick = { viewProfile = profile },
                        label = { Text("${profile.emoji} ${profile.displayName}") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryColor,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpenderProfile.entries.forEach { profile ->
                    FilterChip(
                        selected = currentSpender == profile,
                        onClick = { onCurrentSpenderChange(profile) },
                        label = { Text("Spend as ${profile.displayName}") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SuccessGreen,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }

        // Title
        Text("Today's Spend Radar", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Live dynamic budget radar advising exactly where & how much you can spend safely",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(24.dp))

        // Optimizer Card
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Explore, contentDescription = null, tint = PrimaryColor, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("TODAY'S LIVE SPEND RADAR", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = PrimaryColor, letterSpacing = 1.sp)
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = PrimaryContainerColor,
                    ) {
                        Text(todayDateStr, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = OnPrimaryContainerColor, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("DAILY ALLOCATION\nOPTIMIZER", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = TextPrimary, lineHeight = 28.sp)
                
                Spacer(modifier = Modifier.height(24.dp))

                // Selector nested box
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.background, // F8F9FA
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("SELECT OR CUSTOMIZE\nTODAY'S LIMIT:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextPrimary, letterSpacing = 0.5.sp)
                            
                            val badgeBg = when (mode) {
                                SpendMode.FRUGAL -> Color(0xFFE8F5E9)
                                SpendMode.STANDARD -> Color(0xFFE8EAF6)
                                SpendMode.SPLURGE -> Color(0xFFFFF8E1)
                                SpendMode.CUSTOM -> PrimaryContainerColor
                            }
                            val badgeBorderColor = when (mode) {
                                SpendMode.FRUGAL -> SuccessGreen
                                SpendMode.STANDARD -> PrimaryColor
                                SpendMode.SPLURGE -> WarningAmber
                                SpendMode.CUSTOM -> PrimaryColor
                            }
                            val badgeTextColor = when (mode) {
                                SpendMode.FRUGAL -> SuccessGreen
                                SpendMode.STANDARD -> PrimaryColor
                                SpendMode.SPLURGE -> WarningAmber
                                SpendMode.CUSTOM -> OnPrimaryContainerColor
                            }
                            val badgeLabel = when (mode) {
                                SpendMode.FRUGAL -> "FRUGAL\nTARGET 🌿"
                                SpendMode.STANDARD -> "STANDARD\nTARGET ⚖️"
                                SpendMode.SPLURGE -> "SPLURGE\nTARGET 🍿"
                                SpendMode.CUSTOM -> "CUSTOM\n₹${customDailyLimit.toInt()} ⚙️"
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(2.dp, badgeBorderColor),
                                color = badgeBg,
                                modifier = Modifier.clickable { showCustomLimitDialog = true }
                            ) {
                                Text(
                                    text = badgeLabel,
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = badgeTextColor,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ModePill(title = "Frugal", icon = "🌿", isSelected = mode == SpendMode.FRUGAL) { onModeSelect(SpendMode.FRUGAL) }
                            ModePill(title = "Std", icon = "⚖️", isSelected = mode == SpendMode.STANDARD) { onModeSelect(SpendMode.STANDARD) }
                            ModePill(title = "Splurge", icon = "🍿", isSelected = mode == SpendMode.SPLURGE) { onModeSelect(SpendMode.SPLURGE) }
                            ModePill(title = "Custom", icon = "⚙️", isSelected = mode == SpendMode.CUSTOM) { 
                                onModeSelect(SpendMode.CUSTOM) 
                                showCustomLimitDialog = true
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "\"Standard healthy daily pacing allowing regular rides, grocery store visits, and meals.\"",
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            color = TextSecondary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Bottom metrics box
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Chart
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.background, // F8F9FA
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.weight(0.45f).aspectRatio(0.8f) // Making it roughly tall
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(12.dp).fillMaxSize()
                        ) {
                            Text("UTILIZATION\nSCALE", textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 1.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp)) {
                                CircularProgressIndicator(
                                    progress = 1f,
                                    modifier = Modifier.fillMaxSize(),
                                    color = CardBorder,
                                    strokeWidth = 8.dp,
                                    strokeCap = StrokeCap.Round
                                )
                                CircularProgressIndicator(
                                    progress = progress,
                                    modifier = Modifier.fillMaxSize(),
                                    color = if(progress > 0.85f) ErrorRed else WarningAmber,
                                    strokeWidth = 8.dp,
                                    strokeCap = StrokeCap.Round
                                )
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    Text("USED", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextSecondary, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                    
                    // Values
                    Column(modifier = Modifier.weight(0.55f)) {
                        Text("ALLOWED SPEND TODAY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 1.sp)
                        Text("₹${String.format("%.0f", actualDailyLimit)}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text("CURRENT SPENT TODAY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 1.sp)
                        Text("₹${String.format("%.0f", todayTotal)}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = TextSecondary)
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Column {
                                Text("REMAINING BUFFER", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextPrimary, letterSpacing = 0.5.sp)
                                Text("₹${String.format("%.0f", remaining)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = SuccessGreen)
                            }
                            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.background, border = BorderStroke(1.dp, CardBorder), modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Refresh", modifier = Modifier.padding(8.dp), tint = TextSecondary)
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // SMS Tracking Window
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Smartphone, contentDescription = null, tint = PrimaryColor)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("TODAY'S SMS TRACKING\nWINDOW", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = TextPrimary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Dynamic source receipts currently\nimpacting Today's Spend Radar logic", style = MaterialTheme.typography.bodySmall, color = TextSecondary, lineHeight = 16.sp)
                        }
                    }
                    Surface(shape = RoundedCornerShape(16.dp), color = PrimaryContainerColor, border = BorderStroke(1.dp, PrimaryColor)) {
                        Text("${todayExpenses.size} Messages", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = TextPrimary)
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                todayExpenses.forEachIndexed { index, exp ->
                    DetailedExpenseCard(expense = exp, onDelete = { expenseToDelete = exp.id })
                    if (index < todayExpenses.size - 1) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = CardBorder)
                    }
                }
                
                if (todayExpenses.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No expenses logged today.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(80.dp))

        if (showCustomLimitDialog) {
            var inputValue by remember { mutableStateOf(customDailyLimit.toInt().toString()) }
            AlertDialog(
                onDismissRequest = { showCustomLimitDialog = false },
                title = {
                    Text(
                        text = "Customize Daily Limit ⚙️",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Set a custom daily budget limit for live tracking.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        OutlinedTextField(
                            value = inputValue,
                            onValueChange = { inputValue = it.filter { char -> char.isDigit() } },
                            label = { Text("Daily Limit (₹)") },
                            placeholder = { Text("e.g. 1000") },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            ),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryColor,
                                unfocusedBorderColor = CardBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val limit = inputValue.toDoubleOrNull() ?: 1000.0
                            onCustomLimitChange(limit)
                            onModeSelect(SpendMode.CUSTOM)
                            showCustomLimitDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save Target", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCustomLimitDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = BgColor,
                shape = RoundedCornerShape(24.dp)
            )
        }
    }
}

@Composable
private fun ModePill(title: String, icon: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) TextPrimary else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (isSelected) TextPrimary else CardBorder),
        modifier = Modifier
            .clickable { onClick() }
            .padding(2.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp).widthIn(min = 72.dp)
        ) {
            Text(icon, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title, 
                style = MaterialTheme.typography.labelMedium, 
                fontWeight = FontWeight.Bold, 
                color = if (isSelected) Color.White else TextPrimary
            )
        }
    }
}

private fun parseBankName(sender: String, body: String): String {
    val src = (sender + " " + body).uppercase()
    return when {
        src.contains("SBI") || src.contains("STATE BANK") -> "State Bank of India 🇮🇳"
        src.contains("HDFC") -> "HDFC Bank 🏦"
        src.contains("ICICI") -> "ICICI Bank 🏦"
        src.contains("AXIS") -> "Axis Bank 💳"
        src.contains("KOTAK") -> "Kotak Mahindra Bank 💳"
        src.contains("PAYTM") -> "Paytm Wallet 📱"
        src.contains("GPAY") || src.contains("GOOGLEPAY") -> "Google Pay 📱"
        src.contains("PHONEPE") -> "PhonePe Wallet 📱"
        sender.isNotBlank() -> sender.uppercase()
        else -> "Retail Bank 🏦"
    }
}
