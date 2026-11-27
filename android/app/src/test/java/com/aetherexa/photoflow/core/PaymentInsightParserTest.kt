package com.aetherexa.photoflow.core

import org.junit.Assert.*
import org.junit.Test

class PaymentInsightParserTest {
    @Test fun extractsGpayOutgoingAndMasksSensitiveIdentifiers() {
        val receipt = PaymentInsightParser.parse(listOf(
            "Google Pay", "Payment successful", "Paid to Asha Foods", "₹ 1,250.50",
            "Asha@ybl", "UPI Transaction ID 638492121234"))!!
        assertEquals(PaymentProvider.GPAY, receipt.provider)
        assertEquals(PaymentDirection.OUTGOING, receipt.direction)
        assertEquals(PaymentStatus.SUCCESS, receipt.status)
        assertEquals(125050L, receipt.amountPaise)
        assertEquals("Asha Foods", receipt.payee)
        assertEquals("••••@ybl", receipt.maskedUpiId)
        assertEquals("1234", receipt.referenceLast4)
        assertFalse(receipt.needsReview)
        assertEquals(125050L, receipt.estimatedExpensePaise)
        val tags = receipt.searchableTags.joinToString()
        assertTrue(tags.contains("Asha Foods"))
        assertFalse(tags.contains("638492121234"))
        assertFalse(tags.contains("Asha@ybl"))
    }

    @Test fun paidAmountInlineIsRecognizedButNotConfusedWithPromotionalAmounts() {
        val result = PaymentInsightParser.parse(listOf(
            "Google Pay", "Paid ₹450 to Tea Stall", "UPI Ref 123456789012"))!!
        assertEquals(PaymentDirection.OUTGOING, result.direction)
        assertEquals(PaymentStatus.SUCCESS, result.status)
        assertEquals("Tea Stall", result.payee)
        assertEquals(45000L, result.amountPaise)
    }

    @Test fun personalNumbersNeverAppearInSearchablePayeeTags() {
        val result = PaymentInsightParser.parse(listOf(
            "Google Pay", "Payment successful", "Paid to Rajesh 9876543210", "₹1200",
            "UPI Ref 123456789012"))!!
        assertFalse(result.searchableTags.joinToString().contains("9876543210"))
    }

    @Test fun phonepeFailedPaymentIsNotAnExpense() {
        val result = PaymentInsightParser.parse(listOf(
            "PhonePe", "Payment failed", "Paid to Vinay", "Amount ₹750.00"))!!
        assertEquals(PaymentProvider.PHONEPE, result.provider)
        assertEquals(PaymentStatus.FAILED, result.status)
        assertTrue(result.needsReview)
        assertEquals(0L, result.estimatedExpensePaise)
    }

    @Test fun receivedPaymentIsNotAnExpense() {
        val result = PaymentInsightParser.parse(listOf(
            "PhonePe", "Payment successful", "Received from Riya", "₹500", "UPI Ref 123456789101"))!!
        assertEquals(PaymentDirection.INCOMING, result.direction)
        assertEquals("Riya", result.payee)
        assertEquals(0L, result.estimatedExpensePaise)
    }

    @Test fun cashbackAdvertisementIsNotRecordedAsPayment() {
        assertNull(PaymentInsightParser.parse(listOf(
            "PhonePe", "Get ₹500 cashback today", "Exclusive discount offer!",
            "Try UPI payment now")))
    }

    @Test fun bankBalanceDoesNotOverridePaidAmount() {
        val receipt = PaymentInsightParser.parse(listOf(
            "Paytm", "Payment successful", "Paid to Local Store", "Amount paid ₹85.00",
            "Available balance ₹99,000.00", "UPI Ref 123456789012"))!!
        assertEquals(8500L, receipt.amountPaise)
    }

    @Test fun pendingAndUnknownAmountsRequireReview() {
        val result = PaymentInsightParser.parse(listOf(
            "BHIM UPI", "Transaction pending", "Sent to Merchant", "Payment processing"))!!
        assertTrue(result.needsReview)
        assertNull(result.amountPaise)
        assertEquals(0L, result.estimatedExpensePaise)
    }

    @Test fun screenshotCandidatesDoNotAnalyzeCameraPhotos() {
        assertTrue(ScreenshotSignals.shouldReadText("Screenshot_2026-11-25.png", 1080, 2400, null))
        assertTrue(ScreenshotSignals.shouldReadText("IMG-20261125-WA0010.jpg", 1080, 2400, null))
        assertFalse(ScreenshotSignals.shouldReadText("IMG_0012.jpg", 1080, 2400, "Pixel 8"))
        assertFalse(ScreenshotSignals.shouldReadText("IMG_0012.heic", 1200, 2600, null))
    }

    @Test fun screenshotFilenameMayProvideCaptureTimeWhenExifIsMissing() {
        assertEquals("2026-11-25 11:58:21", PhotoTagEngine.parseScreenshotFilename("Screenshot_20261125_115821.png"))
        assertEquals("2026-11-25 11:58:21", PhotoTagEngine.parseScreenshotFilename("Screenshot-2026-11-25-11-58-21.jpg"))
        assertNull(PhotoTagEngine.parseScreenshotFilename("IMG-20261125-WA0088.jpg"))
    }

    @Test fun noLocationIsInferredFromPaymentOCR() {
        val result = PaymentInsightParser.parse(listOf("Google Pay", "Payment successful", "Paid to Mumbai Cafe", "₹320"))!!
        assertTrue("Payee: Mumbai Cafe" in result.searchableTags)
        assertFalse("Mumbai" in result.searchableTags)
    }

    @Test fun enforcesPlausibleCurrencyAndMasksReferenceSuffix() {
        assertNull(PaymentInsightParser.parseAmount("0"))
        assertNull(PaymentInsightParser.parseAmount("123456789101112131"))
        assertEquals(1099L, PaymentInsightParser.parseAmount("10.99"))
        assertEquals("₹1,250.50", PaymentInsightParser.formatAmount(125050L))
    }
}
