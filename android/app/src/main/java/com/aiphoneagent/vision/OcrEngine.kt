package com.aiphoneagent.vision

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Vision layer (pixel half). Used when accessibility nodes are missing or
 * meaningless — games, canvas/WebGL screens, image-only UIs.
 */
object OcrEngine {

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    @Volatile
    private var cachedText: String? = null

    @Volatile
    private var cachedAt: Long = 0

    fun lastText(maxAgeMs: Long = 8_000): String? =
        if (System.currentTimeMillis() - cachedAt <= maxAgeMs) cachedText else null

    suspend fun recognize(bitmap: Bitmap): String = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                cachedText = result.text.take(8000)
                cachedAt = System.currentTimeMillis()
                if (cont.isActive) cont.resume(cachedText.orEmpty())
            }
            .addOnFailureListener {
                if (cont.isActive) cont.resume("")
            }
    }
}
