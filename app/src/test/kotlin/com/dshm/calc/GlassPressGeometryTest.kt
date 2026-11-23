package com.dshm.calc

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.unit.Dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassPressGeometryTest {
    private class Face(width: Int, height: Int, override val density: Float) : GraphicsLayerScope by GraphicsLayerScope() {
        override fun Dp.toPx() = value * density
        override val size = Size(width * density, height * density)
    }

    @Test
    fun longEdgesStayBoundedAtBothDensities() {
        for (density in listOf(1f, 3f)) {
            for ((width, height) in listOf(64 to 64, 64 to 160, 256 to 44, 75 to 32)) {
                val face = Face(width, height, density)
                face.applyKeyPress(1f)
                assertEquals(3.4f, height * (1f - face.scaleY), 0.0001f)
                assertEquals(3.4f, width * (1f - face.scaleX), 0.0001f)
                face.applyKeyPress(0f)
                assertEquals(1f, face.scaleX, 0f)
                assertEquals(1f, face.scaleY, 0f)
                assertEquals(0f, face.translationX, 0f)
                assertEquals(0f, face.translationY, 0f)
            }
        }
    }

    @Test
    fun edgePressAddsTravelAlongThatAxisAndMovesAwayFromTheFinger() {
        val left = Face(64, 64, 2f)
        val right = Face(64, 64, 2f)
        left.applyKeyPress(1f, Offset(-1f, 0f))
        right.applyKeyPress(1f, Offset(1f, 0f))
        assertEquals(left.scaleX, right.scaleX, 0.00001f)
        assertTrue(left.translationX * right.translationX < 0f)
        assertEquals(left.translationX, -right.translationX, 0.00001f)
        assertEquals(4.3f, 64f * (1f - left.scaleX), 0.0001f)
        assertEquals(3.4f, 64f * (1f - left.scaleY), 0.0001f)
        assertEquals(1.5f * left.density, left.translationX, 0.0001f)
    }
}
