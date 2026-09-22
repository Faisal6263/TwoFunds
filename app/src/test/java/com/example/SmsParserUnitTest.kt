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
    fun parsesCreditSms() {
        val sms = "Rs.1000.00 has been credited to UPI Faisal62632@ibl. Avl bal Rs.15000."

        val expense = parseExpenseFromSms("AX-ICICIB", sms, 1000L)

        assertNotNull(expense)
        assertEquals(1000.0, expense!!.amount, 0.01)
        assertEquals(TransactionType.CREDIT.name, expense.transactionType)
    }

    @Test
    fun parsesSalaryCreditWithoutCurrencyPrefix() {
        val sms = "Your salary was credited 45,000.00 to UPI faisal62632@IBL. Avl bal 52,000.00."

        val expense = parseExpenseFromSms("AX-ICICIB", sms, 1000L)

        assertNotNull(expense)
        assertEquals(45000.0, expense!!.amount, 0.01)
        assertEquals("Salary", expense.category)
        assertEquals(TransactionType.CREDIT.name, expense.transactionType)
    }

    @Test
    fun parsesExactIciciMaskedAccountCreditFormat() {
        val sms = "Dear Customer, Acct XX070 is credited with Rs 50000.00 on 30-Jun-26 from NUSRAT JAHAN QA."

        val expense = parseExpenseFromSms("AX-ICICIB", sms, 1000L)

        assertNotNull(expense)
        assertEquals(50000.0, expense!!.amount, 0.01)
        assertEquals(TransactionType.CREDIT.name, expense.transactionType)
        assertEquals("AX-ICICIB", expense.sourceSender)
    }

    @Test
    fun ignoresCreditWithoutTrackedUpi() {
        val sms = "Rs.2000.00 has been credited to UPI someoneelse@ibl. Avl bal Rs.15000."

        assertNull(parseExpenseFromSms("AX-ICICIB", sms, 1000L))
    }

    @Test
    fun ignoresGenericCreditWithoutOwnerUpi() {
        val sms = "Rs.2000.00 has been credited to your A/c XX1234. Avl bal Rs.15000."

        assertNull(parseExpenseFromSms("AX-ICICIB", sms, 1000L))
    }

    @Test
    fun ignoresCreditToDifferentMaskedAccount() {
        val sms = "Dear Customer, Acct XX071 is credited with Rs 50000.00 on 30-Jun-26 from NUSRAT JAHAN QA."

        assertNull(parseExpenseFromSms("AX-ICICIB", sms, 1000L))
    }

    @Test
    fun ignoresTrackedUpiCreditAtAnotherBank() {
        val sms = "Rs.2000.00 has been credited to UPI Faisal62632@ibl. Avl bal Rs.15000."

        assertNull(parseExpenseFromSms("VK-SBIBK", sms, 1000L))
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
