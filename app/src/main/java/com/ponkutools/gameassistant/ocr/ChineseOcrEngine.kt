package com.ponkutools.gameassistant.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions

class ChineseOcrEngine {
    private val recognizer = TextRecognition.getClient(
        ChineseTextRecognizerOptions.Builder().build(),
    )

    fun recognize(bitmap: Bitmap, callback: (Result<List<OcrCandidate>>) -> Unit) {
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                val candidates = buildList {
                    result.textBlocks.forEach { block ->
                        block.lines.forEach { line ->
                            line.boundingBox?.let { bounds ->
                                add(
                                    OcrCandidate(
                                        line.text,
                                        Bounds(bounds.left, bounds.top, bounds.right, bounds.bottom),
                                        CandidateLevel.LINE,
                                    ),
                                )
                            }
                            line.elements.forEach { element ->
                                element.boundingBox?.let { bounds ->
                                    add(
                                        OcrCandidate(
                                            element.text,
                                            Bounds(bounds.left, bounds.top, bounds.right, bounds.bottom),
                                            CandidateLevel.ELEMENT,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
                callback(Result.success(candidates))
            }
            .addOnFailureListener { callback(Result.failure(it)) }
    }

    fun close() {
        recognizer.close()
    }
}

