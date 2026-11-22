package com.dshm.calc

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsLayerScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassPressGeometryTest {
    private class Face(width: Int, height: Int, override val density: Float) : GraphicsLayerScope by GraphicsLayerScope() {
        override val size = Size(width * density, height * density)
    }

    @Test
    fun longEdgesStayBoundedAtBothDensities() {
        for (density in listOf(1f, 3f)) {
            for ((width, height) in listOf(64 to 64, 64 to 160, 256 to 44, 75 to 32)) {
                val face = Face(width, height, density)
                face.applyGlassPress(1f)
                val depth = height * (1f - face.scaleY)
                assertTrue("Expected visible compression on ${width}x${height}", depth in 1f..6.5f)
                assertTrue(kotlin.math.abs(width * (1f - face.scaleX)) <= 3f)
                face.applyGlassPress(0f)
                assertEquals(1f, face.scaleX, 0f)
                assertEquals(1f, face.scaleY, 0f)
                assertEquals(0f, face.translationX, 0f)
                assertEquals(0f, face.translationY, 0f)
            }
        }
    }

    @Test
    fun oppositeContactsMirrorAndReboundStaysSmall() {
        val left = Face(64, 64, 2f)
        val right = Face(64, 64, 2f)
        left.applyGlassPress(1f, Offset(-1f, 0f))
        right.applyGlassPress(1f, Offset(1f, 0f))
        assertEquals(left.scaleX, right.scaleX, 0.00001f)
        assertTrue(left.translationX * right.translationX < 0f)
        assertEquals(left.translationX, -right.translationX, 0.00001f)
        left.applyGlassPress(-0.04f)
        assertTrue(64f * (left.scaleY - 1f) in 0f..0.25f)
    }
}
