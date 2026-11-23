package com.dshm.calc

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class GlassPressMotionTest {
    private class Frames(parent: CoroutineScope) {
        var factor = 1f
        private var time = 0L
        private val clock = BroadcastFrameClock()
        private val scale = object : MotionDurationScale { override val scaleFactor get() = factor }
        val scope = CoroutineScope(parent.coroutineContext + clock + scale)

        suspend fun advance(count: Int, sample: () -> Float = { 0f }) = buildList {
            repeat(count) {
                Snapshot.sendApplyNotifications()
                yield()
                time += 8_000_000L
                clock.sendFrame(time)
                yield()
                add(sample())
            }
        }
    }

    @Test fun draggingBeyondTheEdgeHoldsTheKeyUntilLiftThenOnlyItsOwnFaceFades() = runBlocking {
        val frames = Frames(this)
        val keypad = KeypadLights()
        val key = KeyLightSource(keypad)
        val light = frames.scope.launch(start = CoroutineStart.UNDISPATCHED) { key.followPressure() }
        try {
            key.beginPress(Offset.Zero)
            key.movePointer(Offset(-40f, -50f))
            frames.advance(18)
            assertEquals(Offset.Zero, key.localContact)
            assertEquals(listOf(key), keypad.emitters.toList())
            assertEquals(1f, key.pressure.value, 0f)
            key.finishPress()
            assertTrue(keypad.emitters.isEmpty())
            assertEquals(1f, key.pressure.value, 0f)
            val release = frames.advance(28) { key.pressure.value }
            assertTrue(release.zipWithNext().all { (a, b) -> b <= a })
            assertEquals(0f, release.last(), 0f)
        } finally {
            light.cancelAndJoin()
        }
    }

    @Test fun reducedMotionSettlesAtThePhysicalKeyState() = runBlocking {
        val frames = Frames(this)
        frames.factor = 0f
        val key = KeyLightSource(KeypadLights())
        val light = frames.scope.launch(start = CoroutineStart.UNDISPATCHED) { key.followPressure() }
        try {
            key.beginPress(Offset.Zero)
            frames.advance(2)
            assertEquals(1f, key.pressure.value, 0f)
            key.finishPress()
            frames.advance(3)
            assertEquals(0f, key.pressure.value, 0f)
        } finally {
            light.cancelAndJoin()
        }
    }

    @Test fun shortKeyContactCompletesTheOriginalHalfDepthPulse() = runBlocking {
        val frames = Frames(this)
        val key = KeyLightSource(KeypadLights())
        val light = frames.scope.launch(start = CoroutineStart.UNDISPATCHED) { key.followPressure() }
        try {
            key.beginPress(Offset.Zero)
            repeat(8) {
                if (key.pressure.value == 0f) frames.advance(1)
            }
            assertTrue(key.pressure.value > 0.01f && key.pressure.value < 0.52f)
            key.finishPress()
            val release = frames.advance(32) { key.pressure.value }
            assertEquals(0.52f, release.max(), 0.001f)
            assertEquals(0f, release.last(), 0f)
        } finally {
            light.cancelAndJoin()
        }
    }
}
