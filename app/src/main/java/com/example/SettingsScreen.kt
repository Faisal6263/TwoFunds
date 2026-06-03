package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CardBorder
import com.example.ui.theme.PrimaryColor
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    mode: SpendMode,
    customDailyLimit: Double,
    dailyPacingLimit: Double,
    monthlyBudget: Double,
    weeklyBudget: Double,
    weekendAllowance: Double,
    currentSpender: SpenderProfile,
    totalTransactions: Int,
    syncMessage: String?,
    onModeSelect: (SpendMode) -> Unit,
    onCustomLimitChange: (Double) -> Unit,
    onDailyPacingChange: (Double) -> Unit,
    onMonthlyBudgetChange: (Double) -> Unit,
    onWeeklyBudgetChange: (Double) -> Unit,
    onWeekendAllowanceChange: (Double) -> Unit,
    onCurrentSpenderChange: (SpenderProfile) -> Unit,
    onOpenSmsSync: () -> Unit,
    onOpenMonthlyLedger: () -> Unit,
    onOpenMonthlyBudget: () -> Unit
) {
    var customLimitInput by remember(customDailyLimit) { mutableStateOf(customDailyLimit.toInt().toString()) }
    var dailyPacingInput by remember(dailyPacingLimit) { mutableStateOf(dailyPacingLimit.toInt().toString()) }
    var monthlyBudgetInput by remember(monthlyBudget) { mutableStateOf(monthlyBudget.toInt().toString()) }
    var weeklyBudgetInput by remember(weeklyBudget) { mutableStateOf(weeklyBudget.toInt().toString()) }
    var weekendAllowanceInput by remember(weekendAllowance) { mutableStateOf(weekendAllowance.toInt().toString()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary
            )
            Text(
                text = "Tune pacing, sync, and ledger behavior.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Settings, contentDescription = null, tint = PrimaryColor)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Budget profile ⚙️", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                Text("Default spender for new expenses", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SpenderProfile.entries.forEach { profile ->
                        FilterChip(
                            selected = currentSpender == profile,
                            onClick = { onCurrentSpenderChange(profile) },
                            label = { Text("${profile.emoji} ${profile.displayName}") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryColor,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SpendMode.entries.forEach { option ->
                        FilterChip(
                            selected = mode == option,
                            onClick = { onModeSelect(option) },
                            label = { Text(option.displayName) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = customLimitInput,
                    onValueChange = { customLimitInput = it.filter(Char::isDigit) },
                    label = { Text("Custom daily limit") },
                    prefix = { Text("Rs.") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = dailyPacingInput,
                    onValueChange = { dailyPacingInput = it.filter(Char::isDigit) },
                    label = { Text("Weekday daily pacing") },
                    prefix = { Text("Rs.") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = monthlyBudgetInput,
                    onValueChange = { monthlyBudgetInput = it.filter(Char::isDigit) },
                    label = { Text("Monthly budget") },
                    prefix = { Text("Rs.") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = weeklyBudgetInput,
                    onValueChange = { weeklyBudgetInput = it.filter(Char::isDigit) },
                    label = { Text("Weekly budget") },
                    prefix = { Text("Rs.") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = weekendAllowanceInput,
                    onValueChange = { weekendAllowanceInput = it.filter(Char::isDigit) },
                    label = { Text("Weekend ride allowance") },
                    prefix = { Text("Rs.") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        customLimitInput.toDoubleOrNull()?.takeIf { it > 0 }?.let(onCustomLimitChange)
                        dailyPacingInput.toDoubleOrNull()?.takeIf { it > 0 }?.let(onDailyPacingChange)
                        monthlyBudgetInput.toDoubleOrNull()?.takeIf { it > 0 }?.let(onMonthlyBudgetChange)
                        weeklyBudgetInput.toDoubleOrNull()?.takeIf { it > 0 }?.let(onWeeklyBudgetChange)
                        weekendAllowanceInput.toDoubleOrNull()?.takeIf { it > 0 }?.let(onWeekendAllowanceChange)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save settings", fontWeight = FontWeight.Bold)
                }
            }
        }

        SettingsShortcut(
            title = "SMS sync",
            subtitle = syncMessage ?: "Import recent bank and UPI SMS transactions.",
            icon = Icons.Outlined.Sms,
            onClick = onOpenSmsSync
        )

        SettingsShortcut(
            title = "Monthly ledger",
            subtitle = "$totalTransactions transactions stored locally.",
            icon = Icons.Outlined.ReceiptLong,
            onClick = onOpenMonthlyLedger
        )

        SettingsShortcut(
            title = "Monthly budget split",
            subtitle = "Divide food, rides, bills, and other category budgets.",
            icon = Icons.Outlined.AccountBalanceWallet,
            onClick = onOpenMonthlyBudget
        )

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            border = BorderStroke(1.dp, PrimaryColor.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null, tint = SuccessGreen)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Local-first storage", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Expenses are saved on this device through Room.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
private fun SettingsShortcut(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = PrimaryColor, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextSecondary)
        }
    }
}
