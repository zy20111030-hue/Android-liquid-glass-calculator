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
        val motion = GlassPressMotion(scope)

        suspend fun advance(count: Int) = buildList {
            repeat(count) {
                Snapshot.sendApplyNotifications()
                yield()
                time += 8_000_000L
                clock.sendFrame(time)
                yield()
                assertTrue(motion.pressure in 0f..1f)
                add(motion.deformation)
            }
        }
    }

    @Test fun tapBetweenFramesIsVisibleButCancellationDoesNotAddPressure() = runBlocking {
        val frames = Frames(this)
        val motion = frames.motion
        motion.press()
        motion.release()
        assertEquals(0.48f, frames.advance(32).max(), 0.005f)
        assertFalse(motion.active)
        motion.press()
        frames.advance(4)
        motion.cancel()
        val cancelled = frames.advance(25)
        assertTrue(cancelled.zipWithNext().all { (a, b) -> b <= a })
        assertEquals(0f, cancelled.min(), 0f)
    }

    @Test fun retriggerContinuesAtCurrentDepthAndDisabledAnimationsFinishImmediately() = runBlocking {
        val frames = Frames(this)
        val motion = frames.motion
        motion.press()
        frames.advance(18)
        motion.release()
        frames.advance(5)
        val interrupted = motion.deformation
        motion.press()
        assertEquals(interrupted, motion.deformation, 0f)
        frames.advance(18)
        motion.release()
        assertEquals(0f, frames.advance(33).min(), 0f)
        frames.factor = 0f
        motion.press()
        assertEquals(1f, motion.pressure, 0f)
        motion.release()
        assertFalse(motion.active)
        assertEquals(0f, motion.deformation, 0f)
    }

    @Test fun cancelledKeyKeepsItsPhysicalLightUntilTheFingerLifts() = runBlocking {
        val frames = Frames(this)
        val key = KeyLightSource(KeypadLights(), frames.motion)
        val light = frames.scope.launch(start = CoroutineStart.UNDISPATCHED) { key.followContactLight() }
        try {
            key.beginPointer(Offset.Zero)
            frames.motion.press()
            frames.advance(18)
            frames.motion.cancel()
            frames.advance(30)
            assertEquals(0f, frames.motion.deformation, 0f)
            assertTrue(key.emitting && key.glow > 0.8f)
            key.endPointer()
            frames.advance(26)
            assertFalse(key.emitting)
            assertEquals(0f, key.glow, 0f)
        } finally {
            light.cancelAndJoin()
        }
    }
}
