package com.nazofobi.arrivalalarm

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

data class ScreenAssistEncodedFrame(
    val jpegBytes: ByteArray,
    val width: Int,
    val height: Int
)

class ScreenAssistFrameEncoder(
    private val maxDimensionPx: Int = 1280,
    private val jpegQuality: Int = 72
) {
    init {
        require(maxDimensionPx > 0)
        require(jpegQuality in 1..100)
    }

    fun encode(source: Bitmap): ScreenAssistEncodedFrame {
        require(source.width > 0 && source.height > 0)
        val largest = max(source.width, source.height)
        val scale = if (largest > maxDimensionPx) maxDimensionPx.toFloat() / largest else 1f
        val targetWidth = (source.width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (source.height * scale).roundToInt().coerceAtLeast(1)
        val scaled = if (targetWidth == source.width && targetHeight == source.height) {
            source
        } else {
            Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
        }

        return try {
            val output = ByteArrayOutputStream()
            check(scaled.compress(Bitmap.CompressFormat.JPEG, jpegQuality, output)) {
                "JPEG compression failed"
            }
            ScreenAssistEncodedFrame(output.toByteArray(), targetWidth, targetHeight)
        } finally {
            if (scaled !== source) scaled.recycle()
        }
    }
}
