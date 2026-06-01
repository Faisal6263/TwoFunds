package com.example

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(
    tableName = "expenses",
    indices = [Index(value = ["originalSms"], unique = true)]
)
@Serializable
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val amount: Double,
    val currency: String,
    val merchant: String,
    val category: String,
    val dateInMillis: Long,
    val originalSms: String
)
