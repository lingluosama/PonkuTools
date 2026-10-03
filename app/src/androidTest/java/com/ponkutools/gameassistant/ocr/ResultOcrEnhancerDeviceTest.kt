package com.ponkutools.gameassistant.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ponkutools.gameassistant.tasks.OcrSnapshot
import com.ponkutools.gameassistant.tasks.forge.ForgeConfig
import com.ponkutools.gameassistant.tasks.forge.ForgeParser
import com.ponkutools.gameassistant.tasks.forge.SoulmateAbilityForgeTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ResultOcrEnhancerDeviceTest {
    @Test
    fun enhancedCurrentCaptureParsesBothRolls() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sourceFile = File(context.filesDir, TEST_IMAGE_NAME)
        assumeTrue("设备未提供结果页测试截图", sourceFile.exists())
        val source = requireNotNull(BitmapFactory.decodeFile(sourceFile.absolutePath))
        val engine = ChineseOcrEngine()
        try {
            val candidates = buildList {
                ResultOcrEnhancer.create(source, source.width, source.height).forEach { region ->
                    region.use { addAll(recognize(engine, it.bitmap).map(it::map)) }
                }
            }
            val recognized = candidates
                .filter { it.level == CandidateLevel.LINE }
                .joinToString(" | ") { it.text }
            val parsed = candidates.filter { it.level == CandidateLevel.LINE }.mapNotNull { ForgeParser.parse(it.text) }
            assertTrue("OCR=$recognized", parsed.any { it.definition.id == "mind" && it.value == 15 })
            assertTrue("OCR=$recognized", parsed.any { it.definition.id == "damage_up" && it.value == 5 })
        } finally {
            engine.close()
            source.recycle()
        }
    }

    @Test
    fun parsesBondCapToCtrResultFromRealCapture() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sourceFile = File(context.filesDir, CTR_TEST_IMAGE_NAME)
        assumeTrue("设备未提供 CTR 结果页测试截图", sourceFile.exists())
        val source = requireNotNull(BitmapFactory.decodeFile(sourceFile.absolutePath))
        val engine = ChineseOcrEngine()
        try {
            val candidates = buildList {
                ResultOcrEnhancer.create(source, source.width, source.height).forEach { region ->
                    region.use { addAll(recognize(engine, it.bitmap).map(it::map)) }
                }
                addAll(recognize(engine, source))
            }
            val snapshot = OcrSnapshot(candidates, source.width, source.height)
            val diagnostics = snapshot.lines.joinToString(" | ") {
                "${it.text}@${it.bounds.left},${it.bounds.top},${it.bounds.right},${it.bounds.bottom}" +
                    if (it.enhanced) "*" else ""
            }
            val result = SoulmateAbilityForgeTask(ForgeConfig()).parseResult(snapshot)
            assertNotNull("OCR=$diagnostics", result)
            assertEquals("OCR=$diagnostics", "bond_cap_up", result?.before?.stat?.definition?.id)
            assertEquals("OCR=$diagnostics", 8, result?.before?.stat?.value)
            assertEquals("OCR=$diagnostics", "ctr_rate", result?.after?.stat?.definition?.id)
            assertEquals("OCR=$diagnostics", 8, result?.after?.stat?.value)
        } finally {
            engine.close()
            source.recycle()
        }
    }

    private fun recognize(engine: ChineseOcrEngine, bitmap: Bitmap): List<OcrCandidate> {
        val latch = CountDownLatch(1)
        var result: Result<List<OcrCandidate>>? = null
        engine.recognize(bitmap) {
            result = it
            latch.countDown()
        }
        assertTrue("OCR 调用超时", latch.await(15, TimeUnit.SECONDS))
        return requireNotNull(result).getOrThrow()
    }

    private companion object {
        const val TEST_IMAGE_NAME = "current-result.png"
        const val CTR_TEST_IMAGE_NAME = "current-result-ctr.png"
    }
}
