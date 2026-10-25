package com.slothshot.app

import com.slothshot.app.model.ActionType
import com.slothshot.app.model.ContentCategory
import com.slothshot.app.model.RetentionClass
import com.slothshot.app.triage.HeuristicTriageEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeuristicTriageTest {

    private val engine = HeuristicTriageEngine()

    @Test
    fun testOtpCodeDetection() {
        val ocr = "Your verification code is 849211. Do not share this OTP with anyone."
        val result = engine.triage(ocr, "Screenshot_20261025.png")

        assertEquals(ContentCategory.TEMPORARY_UTILITY, result.category)
        assertEquals(RetentionClass.EPHEMERAL_PURGE, result.retention)
        assertEquals("849211", result.actionPayload)
        assertEquals(ActionType.COPY, result.actionType)
    }

    @Test
    fun testWifiQrCredentials() {
        val ocr = "WIFI:S:GuestCoffee;T:WPA;P:SuperSecretPass123;;"
        val result = engine.triage(ocr)

        assertEquals(ContentCategory.TEMPORARY_UTILITY, result.category)
        assertEquals(RetentionClass.ACTION_REQUIRED, result.retention)
        assertEquals("SuperSecretPass123", result.actionPayload)
        assertEquals("GuestCoffee", result.keyValues["SSID"])
    }

    @Test
    fun testMovieRecommendation() {
        val ocr = "Dune: Part Two\nIMDb 8.6/10\nDirected by Denis Villeneuve\nSci-Fi Adventure"
        val result = engine.triage(ocr)

        assertEquals(ContentCategory.ENTERTAINMENT_MEDIA, result.category)
        assertEquals(RetentionClass.KEEP_ARCHIVE, result.retention)
        assertEquals(ActionType.SEARCH_WEB, result.actionType)
        assertTrue(result.title.contains("Dune"))
    }

    @Test
    fun testDeliveryTracking() {
        val ocr = "Package in transit\nTracking number: 1Z9999999999999999\nExpected delivery tomorrow"
        val result = engine.triage(ocr)

        assertEquals(ContentCategory.TRANSACTIONAL_RECEIPT, result.category)
        assertEquals(RetentionClass.ACTION_REQUIRED, result.retention)
        assertEquals("1Z9999999999999999", result.actionPayload)
    }

    @Test
    fun testReceiptTotal() {
        val ocr = "Coffee Shop\nLatte $4.50\nCroissant $3.50\nTotal: $8.00\nThank you!"
        val result = engine.triage(ocr)

        assertEquals(ContentCategory.TRANSACTIONAL_RECEIPT, result.category)
        assertEquals(RetentionClass.KEEP_ARCHIVE, result.retention)
        assertTrue(result.title.contains("8.00") || result.summary.contains("8.00"))
    }
}
