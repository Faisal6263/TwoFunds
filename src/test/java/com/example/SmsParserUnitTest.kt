package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SmsParserUnitTest {
    @Test
    fun parsesDebitSmsLocally() {
        val sms = "Rs.540.00 debited from A/c XX1234 at ZOMATO on 03-Jun. Avl bal Rs.12000."

        val expense = parseExpenseFromSms("VM-HDFCBK", sms, 1000L)

        assertNotNull(expense)
        assertEquals(540.0, expense!!.amount, 0.01)
        assertEquals("Food", expense.category)
        assertEquals(sms, expense.originalSms)
    }

    @Test
    fun ignoresCreditSms() {
        val sms = "Rs.1000.00 has been credited to your A/c XX1234. Avl bal Rs.15000."

        val expense = parseExpenseFromSms("VK-SBIBK", sms, 1000L)

        assertNull(expense)
    }

    @Test
    fun parsesUpiPaidToMerchant() {
        val sms = "INR 250 paid to Swiggy via UPI Ref 123456 from your account."

        val expense = parseExpenseFromSms("AX-ICICIB", sms, 1000L)

        assertNotNull(expense)
        assertEquals(250.0, expense!!.amount, 0.01)
        assertEquals("Swiggy", expense.merchant)
        assertEquals("Food", expense.category)
    }
}
