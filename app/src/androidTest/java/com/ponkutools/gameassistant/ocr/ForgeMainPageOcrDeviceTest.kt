package com.ponkutools.gameassistant.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ponkutools.gameassistant.tasks.OcrSnapshot
import com.ponkutools.gameassistant.tasks.forge.ForgePageMatcher
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ForgeMainPageOcrDeviceTest {
    @Test
    fun recognizesRealForgePageFromKeywords() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sourceFile = File(context.filesDir, TEST_IMAGE_NAME)
        assumeTrue("设备未提供能力锻造页面测试截图", sourceFile.exists())
        val source = requireNotNull(BitmapFactory.decodeFile(sourceFile.absolutePath))
        val engine = ChineseOcrEngine()
        try {
            val candidates = recognize(engine, source)
            val snapshot = OcrSnapshot(candidates, source.width, source.height)
            val recognized = snapshot.lines.joinToString(" | ") { it.text }
            assertTrue("OCR=$recognized", ForgePageMatcher.isMainPage(snapshot))
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
        const val TEST_IMAGE_NAME = "current-main-page.png"
    }
}
