package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyDashboardScreen(
    budgetSummary: BudgetSummary,
    weeklyBudget: Double,
    onWeeklyBudgetChange: (Double) -> Unit,
    navController: NavController
) {
    val scrollState = rememberScrollState()
    var showEditBudgetDialog by remember { mutableStateOf(false) }
    var selectedDay by remember { mutableStateOf<DaySpentData?>(null) }

    val totalWeekSpent = budgetSummary.weekTotal
    val progress = budgetSummary.weekProgress

    // Days list Monday to Sunday
    val daysOfWeek = remember {
        listOf(
            Calendar.MONDAY to "Monday 🗓️",
            Calendar.TUESDAY to "Tuesday 🗓️",
            Calendar.WEDNESDAY to "Wednesday 🗓️",
            Calendar.THURSDAY to "Thursday 🗓️",
            Calendar.FRIDAY to "Friday 🗓️",
            Calendar.SATURDAY to "Saturday 🗓️",
            Calendar.SUNDAY to "Sunday 🗓️"
        )
    }

    // Allocate per day budget
    val dailyAllocation = budgetSummary.weekDailyAllocation

    val daySpentList = remember(budgetSummary) {
        daysOfWeek.map { dayPair ->
            val dayExpenses = budgetSummary.weekExpenses.filter { expense ->
                Calendar.getInstance().apply { timeInMillis = expense.dateInMillis }
                    .get(Calendar.DAY_OF_WEEK) == dayPair.first
            }
            DaySpentData(
                dayName = dayPair.second,
                spent = budgetSummary.dailySpentByCalendarDay[dayPair.first] ?: 0.0,
                expenses = dayExpenses.sortedByDescending { it.dateInMillis }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // App Header section with back button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Weekly Budget Monitor",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary
            )
        }

        // Summary Card
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = PrimaryColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "WEEKLY PACE & PACING",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryColor,
                            letterSpacing = 1.sp
                        )
                    }
                    
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF3F4F6),
                        modifier = Modifier.clickable { showEditBudgetDialog = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Budget",
                                tint = TextSecondary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Configure Target",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Weekly Spent vs Allocation",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "₹${String.format("%,.0f", totalWeekSpent)}",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Black,
                            color = if (totalWeekSpent > weeklyBudget) ErrorRed else SuccessGreen
                        )
                        Text(
                            text = "out of target limit ₹${String.format("%,.0f", weeklyBudget)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    
                    val percentLeft = if (weeklyBudget > 0.0) {
                        ((weeklyBudget - totalWeekSpent) / weeklyBudget * 100).coerceAtLeast(0.0)
                    } else {
                        0.0
                    }
                    Text(
                        text = "${percentLeft.toInt()}% remaining",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (percentLeft > 20) SuccessGreen else WarningAmber
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = if (progress >= 1f) ErrorRed else if (progress > 0.8f) WarningAmber else SuccessGreen,
                    trackColor = CardBorder
                )

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (totalWeekSpent <= weeklyBudget) Color(0xFFF0FDF4) else Color(0xFFFEF2F2),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = if (totalWeekSpent <= weeklyBudget) SuccessGreen else ErrorRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (totalWeekSpent <= weeklyBudget) {
                                "Awesome! You are ₹${String.format("%.0f", weeklyBudget - totalWeekSpent)} under your weekly budget."
                            } else {
                                "Warning: Weekly target budget exceeded by ₹${String.format("%.0f", totalWeekSpent - weeklyBudget)}."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (totalWeekSpent <= weeklyBudget) Color(0xFF166534) else Color(0xFF991B1B),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Grid Title
        Text(
            text = "CALENDAR TYPE WEEKLY GRID",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Custom Grid (Not using Nested LazyVerticalGrid inside Column verticalScroll, but using custom Columns/Rows or manual chunking)
        // Manual chunking is infinitely safer to prevent nested scroll exceptions!
        daySpentList.chunked(2).forEach { rowDays ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowDays.forEach { dayData ->
                    val isOver = dayData.spent > dailyAllocation
                    val isZero = dayData.spent == 0.0

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isZero) MaterialTheme.colorScheme.surface
                            else if (isOver) Color(0xFFFEF2F2)
                            else Color(0xFFF0FDF4)
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isZero) CardBorder
                            else if (isOver) ErrorRed.copy(alpha = 0.5f)
                            else SuccessGreen.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedDay = dayData }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = dayData.dayName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Column {
                                    Text(
                                        text = "SPENT",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 8.sp,
                                        color = TextSecondary,
                                        letterSpacing = 0.5.sp
                                    )
                                    Text(
                                        text = "₹${String.format("%.0f", dayData.spent)}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isZero) TextPrimary
                                        else if (isOver) ErrorRed
                                        else SuccessGreen
                                    )
                                    Text(
                                        text = "${dayData.expenses.size} transactions",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary,
                                        fontSize = 10.sp
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "ALLOTED",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 8.sp,
                                        color = TextSecondary,
                                        letterSpacing = 0.5.sp
                                    )
                                    Text(
                                        text = "₹${String.format("%.0f", dailyAllocation)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Status text badge
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isZero) Color(0xFFF3F4F6)
                                else if (isOver) Color(0xFFFEE2E2)
                                else Color(0xFFDCFCE7),
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Text(
                                    text = if (isZero) "No Expenses"
                                    else if (isOver) "Limit Exceeded ⚠️"
                                    else "Under Limit 🌿",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isZero) TextSecondary
                                    else if (isOver) ErrorRed
                                    else SuccessGreen,
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
                if (rowDays.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))

        selectedDay?.let { day ->
            AlertDialog(
                onDismissRequest = { selectedDay = null },
                title = {
                    Column {
                        Text(
                            text = day.dayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "${day.expenses.size} transactions - Rs.${String.format("%,.0f", day.spent)} spent",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                },
                text = {
                    if (day.expenses.isEmpty()) {
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
                            day.expenses.forEach { expense ->
                                DetailedExpenseCard(expense = expense)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selectedDay = null }) {
                        Text("Close", color = PrimaryColor)
                    }
                },
                containerColor = BgColor,
                shape = RoundedCornerShape(24.dp)
            )
        }

        // Dialog for editing weekly budget
        if (showEditBudgetDialog) {
            var inputValue by remember { mutableStateOf(weeklyBudget.toInt().toString()) }
            AlertDialog(
                onDismissRequest = { showEditBudgetDialog = false },
                title = {
                    Text(
                        text = "Set weekly target budget limit ⚙️",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Set a weekly allocation budget to pace your daily couple targets dynamically.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        OutlinedTextField(
                            value = inputValue,
                            onValueChange = { inputValue = it.filter { char -> char.isDigit() } },
                            label = { Text("Weekly Budget (₹)") },
                            placeholder = { Text("e.g. 4000") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number
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
                            val budget = inputValue.toDoubleOrNull() ?: 4000.0
                            onWeeklyBudgetChange(budget)
                            showEditBudgetDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save Budget", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditBudgetDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = BgColor,
                shape = RoundedCornerShape(24.dp)
            )
        }
    }
}

data class DaySpentData(
    val dayName: String,
    val spent: Double,
    val expenses: List<Expense>
)
