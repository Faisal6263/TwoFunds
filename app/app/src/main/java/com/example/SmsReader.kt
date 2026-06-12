package com.example

import android.content.Context
import android.provider.Telephony
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import android.util.Log

data class SmsMessageData(
    val sender: String,
    val body: String,
    val date: Long
)

suspend fun readSms(
    context: Context,
    limit: Int = 1000,
    daysAgo: Int = 7
): List<SmsMessageData> = withContext(Dispatchers.IO) {

    val smsList = mutableListOf<SmsMessageData>()

    val cutoffTime =
        System.currentTimeMillis() - TimeUnit.DAYS.toMillis(daysAgo.toLong())

    try {
        val cursor = context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE
            ),
            "${Telephony.Sms.DATE} > ?",
            arrayOf(cutoffTime.toString()),
            "${Telephony.Sms.DATE} DESC"
        )

        cursor?.use {
            val addressIndex =
                it.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIndex =
                it.getColumnIndex(Telephony.Sms.BODY)
            val dateIndex =
                it.getColumnIndex(Telephony.Sms.DATE)

            while (it.moveToNext() && smsList.size < limit) {
                val sender = if (addressIndex != -1) it.getString(addressIndex) ?: "" else ""
                val body = if (bodyIndex != -1) it.getString(bodyIndex) ?: "" else ""
                val date = if (dateIndex != -1) it.getLong(dateIndex) else 0L

                smsList.add(
                    SmsMessageData(
                        sender = sender,
                        body = body,
                        date = date
                    )
                )
            }
        }
    } catch (e: Exception) {
        Log.e("SMS_DEBUG", "Error reading SMS messages", e)
    }

    Log.d("SMS_DEBUG", "Total SMS read directly from DB: ${smsList.size}")

    smsList
}
