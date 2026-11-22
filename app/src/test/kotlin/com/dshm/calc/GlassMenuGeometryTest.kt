package com.dshm.calc

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassMenuGeometryTest {
    @Test
    fun openingAndClosingKeepTheLongHistoryPillClearInBothDirections() {
        val face = 40f
        val inset = 4f
        val travel = 52f
        val gap = 6f
        for ((width, height) in listOf(252f to 392f, 440f to 245f)) {
            for (direction in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
                val ltr = direction == LayoutDirection.Ltr
                val x = if (ltr) width - face - inset else inset
                val trigger = Rect(x, inset, x + face, inset + face)
                val history = if (ltr) Rect(0f, trigger.top, trigger.left - gap, trigger.bottom)
                    else Rect(trigger.right + gap, trigger.top, width, trigger.bottom)
                fun at(progress: Float) = menuFrame(width, height, face, inset, travel, progress, direction)
                    .translate(Offset(0f, travel))

                assertEquals(trigger, at(0f))
                assertEquals(Rect(0f, travel, width, travel + height), at(1f))
                // Closing retraces these frames. Rectangle bounds include the curved corners.
                for (step in 0..100) {
                    val menu = at(step / 100f)
                    val dx = maxOf(0f, history.left - menu.right, menu.left - history.right)
                    val dy = maxOf(0f, history.top - menu.bottom, menu.top - history.bottom)
                    assertTrue("width=$width, direction=$direction, step=$step", hypot(dx, dy) >= gap - 0.001f)
                }
            }
        }
    }
}
