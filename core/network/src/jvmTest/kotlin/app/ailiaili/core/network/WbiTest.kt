package app.ailiaili.core.network

import kotlin.test.Test
import kotlin.test.assertEquals

class WbiTest {
    @Test
    fun mixinKeyMatchesKnownNavPair() {
        assertEquals(
            "ea1db124af3c7062474693fa704f4ff8",
            mixinKey("7cd084941338484aae1ad9425b84077c", "4932caff0ff746eab6f01bf08b70ac45"),
        )
    }
}
