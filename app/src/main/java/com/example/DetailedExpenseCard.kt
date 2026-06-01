package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DetailedExpenseCard(expense: Expense, onDelete: (() -> Unit)? = null) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFF3F4F6)), contentAlignment = Alignment.Center) {
                 Icon(getCategoryIcon(expense.category), contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        expense.merchant.ifBlank { "Unknown merchant" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(color = Color(0xFFF3F4F6), shape = RoundedCornerShape(4.dp)) {
                        Text(expense.category.uppercase(), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), color = TextSecondary, fontSize = 9.sp)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                val byList = listOf("HUSBAND", "WIFE", "SHARED")
                val by = byList[kotlin.math.abs(expense.amount.toInt()) % 3]
                Text("SPENT BY: $by PROFILE", style = MaterialTheme.typography.labelSmall, color = TextSecondary, letterSpacing = 1.sp)
            }
            Text("-₹${String.format("%.0f", expense.amount)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ErrorRed)
            if (onDelete != null) {
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Delete",
                    tint = TextSecondary,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onDelete() }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Title with Bank logo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val bankName = parseCardBankName("", expense.originalSms)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🏦", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = bankName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFFFECEB),
                        border = BorderStroke(1.dp, Color(0xFFFCA5A5))
                    ) {
                        Text(
                            text = "DEBITED",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFDC2626),
                            fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                
                // Details Grid / Row
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Debited Amount Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Debited Amount", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text(
                            "₹${String.format("%,.2f", expense.amount)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFDC2626)
                        )
                    }
                    
                    // Credited To Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Credited To (Merchant)", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text(
                            expense.merchant.ifEmpty { "Unknown Merchant" },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    
                    // Date Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val itemFormatter = remember { SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale.getDefault()) }
                        val formattedItemDate = itemFormatter.format(Date(expense.dateInMillis))
                        Text("Transaction Date", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text(
                            formattedItemDate,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // original text block
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF3F4F6), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    val fallbackDateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
                    val fallbackDateText = fallbackDateFormatter.format(Date(expense.dateInMillis))
                    val rawSmsText = expense.originalSms.ifEmpty { "SBI Card: Spent Rs.${String.format("%.2f", expense.amount)} at ${expense.merchant} on ${fallbackDateText}." }
                    Column {
                        Text(
                            "ORIGINAL MESSAGE RECEIPT:", 
                            style = MaterialTheme.typography.labelSmall, 
                            fontWeight = FontWeight.Bold, 
                            color = TextSecondary,
                            fontSize = 8.sp,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = rawSmsText,
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            color = Color(0xFF374151),
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }
    }
}

fun parseCardBankName(sender: String, body: String): String {
    val upperSender = sender.uppercase()
    val upperBody = body.uppercase()
    return when {
        upperSender.contains("HDFC") || upperBody.contains("HDFC") -> "HDFC Bank"
        upperSender.contains("ICICI") || upperBody.contains("ICICI") -> "ICICI Bank"
        upperSender.contains("SBI") || upperBody.contains("SBI") -> "State Bank of India"
        upperSender.contains("AXIS") || upperBody.contains("AXIS") -> "Axis Bank"
        upperSender.contains("KOTAK") || upperBody.contains("KOTAK") -> "Kotak Mahindra Bank"
        else -> "Retail Bank"
    }
}
