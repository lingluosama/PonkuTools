package com.ponkutools.gameassistant.automation

import com.ponkutools.gameassistant.ocr.Bounds
import org.junit.Assert.assertEquals
import org.junit.Test

class CoordinateMapperTest {
    @Test
    fun relativeTapReportsDesignAndNormalizedCoordinates() {
        val tap = CoordinateMapper.relativeTap(270, 480, 1080, 1920)
        assertEquals(135, tap.designX)
        assertEquals(240, tap.designY)
        assertEquals(0.25f, tap.normalizedX, 0.001f)
        assertEquals(0.25f, tap.normalizedY, 0.001f)
    }

    @Test
    fun mapsDesignCanvasInsideTallScreenLetterbox() {
        val topLeft = CoordinateMapper.designToScreen(0, 0, 1080, 2376)
        val center = CoordinateMapper.designToScreen(270, 480, 1080, 2376)
        val bottomRight = CoordinateMapper.designToScreen(540, 960, 1080, 2376)

        assertEquals(ScreenPoint(0, 228), topLeft)
        assertEquals(ScreenPoint(540, 1188), center)
        assertEquals(ScreenPoint(1079, 2148), bottomRight)
    }

    @Test
    fun relativeTapRemovesLetterboxOffset() {
        val tap = CoordinateMapper.relativeTap(540, 1188, 1080, 2376)

        assertEquals(270, tap.designX)
        assertEquals(480, tap.designY)
    }
    @Test
    fun mapsImageCenterToScreenCenter() {
        val point = CoordinateMapper.centerToScreen(
            bounds = Bounds(400, 200, 600, 400),
            imageWidth = 1000,
            imageHeight = 600,
            screenWidth = 2000,
            screenHeight = 1200,
        )

        assertEquals(1000, point.x)
        assertEquals(600, point.y)
    }
}

