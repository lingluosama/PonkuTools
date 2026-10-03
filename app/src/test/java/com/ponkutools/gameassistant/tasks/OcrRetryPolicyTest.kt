package com.ponkutools.gameassistant.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class OcrRetryPolicyTest {
    @Test
    fun dailyPolicyDoesNotChangeForgeCadence() {
        assertEquals(5, OcrRetryPolicy.FORGE.maxAttempts)
        assertEquals(200L, OcrRetryPolicy.FORGE.intervalMs)
        assertEquals(10, OcrRetryPolicy.DAILY_PAGE.maxAttempts)
        assertEquals(500L, OcrRetryPolicy.DAILY_PAGE.intervalMs)
        assertNotEquals(OcrRetryPolicy.FORGE, OcrRetryPolicy.DAILY_PAGE)
    }
}
