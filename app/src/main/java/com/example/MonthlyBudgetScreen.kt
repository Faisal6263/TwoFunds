package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.TwoWheeler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.ui.theme.BgColor
import com.example.ui.theme.CardBorder
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.PrimaryColor
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyBudgetScreen(
    budgetSummary: BudgetSummary,
    monthlyBudget: Double,
    categoryBudgets: Map<String, Double>,
    onMonthlyBudgetChange: (Double) -> Unit,
    onCategoryBudgetChange: (String, Double) -> Unit,
    navController: NavController
) {
    var showMonthlyDialog by remember { mutableStateOf(false) }
    var categoryToEdit by remember { mutableStateOf<String?>(null) }

    val monthlyExpenses = budgetSummary.monthlyExpenses
    val spentByCategory = remember(monthlyExpenses) {
        monthlyExpenses.groupBy { normalizeBudgetCategory(it.category) }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
    }
    val totalSpent = budgetSummary.monthlyTotal
    val assignedBudget = BudgetCategories.sumOf { categoryBudgets[it] ?: 0.0 }
    val monthName = budgetSummary.monthName
    val monthlyProgress = budgetSummary.monthlyProgress

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Monthly Budget Split", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(monthName, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgColor)
            )
        },
        containerColor = BgColor
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2563EB)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("MONTHLY PLAN 🧾", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.78f))
                                Text("₹${String.format("%,.0f", totalSpent)} / ₹${String.format("%,.0f", monthlyBudget)}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = Color.White)
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White.copy(alpha = 0.14f),
                                modifier = Modifier.clickable { showMonthlyDialog = true }
                            ) {
                                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Edit", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                        LinearProgressIndicator(
                            progress = monthlyProgress,
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = if (monthlyProgress > 0.9f) WarningAmber else SuccessGreen,
                            trackColor = Color.White.copy(alpha = 0.24f)
                        )
                        Text("Assigned across categories: ₹${String.format("%,.0f", assignedBudget)}", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.82f))
                    }
                }
            }

            item {
                Text("CATEGORY BUDGETS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.padding(top = 8.dp))
            }

            items(BudgetCategories, key = { it }) { category ->
                val budget = categoryBudgets[category] ?: 0.0
                val spent = spentByCategory[category] ?: 0.0
                CategoryBudgetRow(
                    category = category,
                    budget = budget,
                    spent = spent,
                    onEdit = { categoryToEdit = category }
                )
            }
        }
    }

    if (showMonthlyDialog) {
        BudgetAmountDialog(
            title = "Set monthly budget",
            currentAmount = monthlyBudget,
            onDismiss = { showMonthlyDialog = false },
            onSave = {
                onMonthlyBudgetChange(it)
                showMonthlyDialog = false
            }
        )
    }

    categoryToEdit?.let { category ->
        BudgetAmountDialog(
            title = "Set $category budget",
            currentAmount = categoryBudgets[category] ?: 0.0,
            onDismiss = { categoryToEdit = null },
            onSave = {
                onCategoryBudgetChange(category, it)
                categoryToEdit = null
            }
        )
    }
}

@Composable
private fun CategoryBudgetRow(category: String, budget: Double, spent: Double, onEdit: () -> Unit) {
    val progress = if (budget > 0) (spent / budget).toFloat().coerceIn(0f, 1f) else 0f
    val isOver = budget > 0 && spent > budget
    val icon = if (category == "Rides") Icons.Outlined.TwoWheeler else Icons.Outlined.PieChart

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (isOver) ErrorRed.copy(alpha = 0.45f) else CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(38.dp).clip(CircleShape).background(if (category == "Rides") Color(0xFFFFEDD5) else Color(0xFFE0F2FE)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = if (category == "Rides") WarningAmber else PrimaryColor, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(if (category == "Rides") "Rides 🛵" else category, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Spent ₹${String.format("%,.0f", spent)} of ₹${String.format("%,.0f", budget)}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF3F4F6),
                    modifier = Modifier.clickable(onClick = onEdit)
                ) {
                    Text("Modify", modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
            }
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
                color = if (isOver) ErrorRed else if (progress > 0.8f) WarningAmber else SuccessGreen,
                trackColor = CardBorder
            )
        }
    }
}

@Composable
private fun BudgetAmountDialog(
    title: String,
    currentAmount: Double,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var input by remember(currentAmount) { mutableStateOf(currentAmount.toInt().toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.filter(Char::isDigit) },
                label = { Text("Budget amount") },
                prefix = { Text("Rs.") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryColor, unfocusedBorderColor = CardBorder),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { input.toDoubleOrNull()?.takeIf { it >= 0 }?.let(onSave) },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = BgColor
    )
}
