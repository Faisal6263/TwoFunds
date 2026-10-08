package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.example.ui.theme.CardBorder
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.PrimaryColor
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber

private data class RideIdea(
    val title: String,
    val distanceKm: Float,
    val mealBudget: Float,
    val buffer: Float
)

@Composable
fun RidePlannerScreen(rideBudget: Double) {
    val ideas = remember {
        listOf(
            RideIdea("Nandi Hills Sunrise", 60f, 700f, 300f),
            RideIdea("Local Coffee Hop", 5f, 250f, 100f),
            RideIdea("City Errand Loop", 10f, 300f, 120f),
            RideIdea("Lonavala Cafe", 85f, 1200f, 450f),
            RideIdea("Mulshi Lake", 120f, 1400f, 600f),
            RideIdea("Highway Cafe", 45f, 500f, 250f)
        )
    }
    var selectedIdea by remember { mutableStateOf(ideas.first()) }
    var oneWayDistance by remember { mutableFloatStateOf(selectedIdea.distanceKm) }
    var mileage by remember { mutableFloatStateOf(38f) }
    var fuelPrice by remember { mutableFloatStateOf(105f) }
    var mealBudget by remember { mutableFloatStateOf(selectedIdea.mealBudget) }
    var buffer by remember { mutableFloatStateOf(selectedIdea.buffer) }

    val weekendFund = safeBudget(rideBudget)
    val estimate = buildRideEstimate(oneWayDistance.toDouble(), mileage.toDouble(), fuelPrice.toDouble(), mealBudget.toDouble(), buffer.toDouble(), weekendFund)
    val fuelCost = estimate.fuelCost
    val totalCost = estimate.totalCost
    val remaining = estimate.remaining
    val progress = budgetProgress(totalCost, weekendFund)
    val isSafe = totalCost <= weekendFund

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text("Ride Planner", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
            Text("Estimate fuel, food, and buffer before committing weekend spend.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = PrimaryColor)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Weekend fund estimate", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        Text("Rs.${formatMoney(weekendFund)} available", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    Icon(
                        imageVector = if (isSafe) Icons.Filled.CheckCircle else Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = if (isSafe) SuccessGreen else WarningAmber
                    )
                }

                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (isSafe) SuccessGreen else ErrorRed,
                    trackColor = CardBorder
                )

                Text(
                    text = if (isSafe) {
                        "Plan is within budget with Rs.${formatMoney(remaining)} left."
                    } else {
                        "Plan exceeds budget by Rs.${formatMoney(-remaining)}."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSafe) SuccessGreen else ErrorRed
                )
                Text("Estimate capped by remaining weekly and monthly budgets; based on recorded transactions and current pacing settings.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }

        Text("Trip ideas 🛵", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = PrimaryColor)
        ideas.chunked(2).forEach { rowIdeas ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowIdeas.forEach { idea ->
                    RecommendationCard(
                        idea = idea,
                        selected = selectedIdea == idea,
                        modifier = Modifier.weight(1f)
                    ) {
                        selectedIdea = idea
                        oneWayDistance = idea.distanceKm
                        mealBudget = idea.mealBudget
                        buffer = idea.buffer
                    }
                }
                if (rowIdeas.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        CostCalculatorCard(
            oneWayDistance = oneWayDistance,
            mileage = mileage,
            fuelPrice = fuelPrice,
            mealBudget = mealBudget,
            buffer = buffer,
            fuelCost = fuelCost,
            totalCost = totalCost,
            onDistanceChange = { oneWayDistance = it },
            onMileageChange = { mileage = it },
            onFuelPriceChange = { fuelPrice = it },
            onMealBudgetChange = { mealBudget = it },
            onBufferChange = { buffer = it }
        )

        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
private fun RecommendationCard(
    idea: RideIdea,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, if (selected) PrimaryColor else CardBorder),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Place, contentDescription = null, tint = PrimaryColor, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(idea.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            Text("${idea.distanceKm.toInt()} km one-way", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Text("Est. Rs.${formatMoney(idea.mealBudget + idea.buffer)} before fuel", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = PrimaryColor)
        }
    }
}

@Composable
private fun CostCalculatorCard(
    oneWayDistance: Float,
    mileage: Float,
    fuelPrice: Float,
    mealBudget: Float,
    buffer: Float,
    fuelCost: Double,
    totalCost: Double,
    onDistanceChange: (Float) -> Unit,
    onMileageChange: (Float) -> Unit,
    onFuelPriceChange: (Float) -> Unit,
    onMealBudgetChange: (Float) -> Unit,
    onBufferChange: (Float) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.LocalCafe, contentDescription = null, tint = PrimaryColor)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Live cost calculator", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
            }

            DistanceInput(oneWayDistance, onDistanceChange)
            PlannerSlider("One-way distance", "${oneWayDistance.toInt()} km", oneWayDistance, 0f..250f, onDistanceChange)
            PlannerSlider("Vehicle mileage", "${mileage.toInt()} km/l", mileage, 15f..70f, onMileageChange)
            PlannerSlider("Fuel price", "Rs.${fuelPrice.toInt()}/l", fuelPrice, 80f..130f, onFuelPriceChange)
            PlannerSlider("Food and stops", "Rs.${mealBudget.toInt()}", mealBudget, 0f..3000f, onMealBudgetChange)
            PlannerSlider("Safety buffer", "Rs.${buffer.toInt()}", buffer, 0f..1500f, onBufferChange)

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CostRow("Fuel estimate", fuelCost)
                    CostRow("Food and buffer", roundMoney(mealBudget.toDouble() + buffer.toDouble()))
                    CostRow("Total ride cost", totalCost, emphasized = true)
                }
            }
        }
    }
}

@Composable
private fun PlannerSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Text(valueText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = PrimaryColor)
        }
        Slider(
            value = value,
            onValueChange = { onValueChange(kotlin.math.round(it)) },
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = PrimaryColor,
                activeTrackColor = PrimaryColor
            )
        )
    }
}

@Composable
private fun DistanceInput(
    oneWayDistance: Float,
    onDistanceChange: (Float) -> Unit
) {
    var distanceInput by remember(oneWayDistance) { mutableStateOf(oneWayDistance.toInt().toString()) }

    OutlinedTextField(
        value = distanceInput,
        onValueChange = { value ->
            val cleaned = value.filter(Char::isDigit).take(3)
            distanceInput = cleaned
            cleaned.toFloatOrNull()?.let { onDistanceChange(it.coerceIn(0f, 250f)) }
        },
        label = { Text("Exact one-way distance") },
        suffix = { Text("km") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PrimaryColor,
            unfocusedBorderColor = CardBorder
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun CostRow(label: String, amount: Double, emphasized: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(
            "Rs.${formatMoney(amount)}",
            style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasized) FontWeight.Black else FontWeight.Bold,
            color = if (emphasized) TextPrimary else PrimaryColor
        )
    }
}
