package app.ailiaili.core.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LiveStartTransitionTest {
    @Test
    fun firesOnlyOnOfflineToLive() {
        assertTrue(liveStartTransition(0, 1))
        assertTrue(liveStartTransition(2, 1))
    }

    @Test
    fun firstSightNeverFires() {
        assertFalse(liveStartTransition(null, 1))
        assertFalse(liveStartTransition(null, 0))
    }

    @Test
    fun steadyStatesNeverFire() {
        assertFalse(liveStartTransition(1, 1))
        assertFalse(liveStartTransition(0, 0))
        assertFalse(liveStartTransition(1, 0))
        assertFalse(liveStartTransition(0, 2))
    }
}