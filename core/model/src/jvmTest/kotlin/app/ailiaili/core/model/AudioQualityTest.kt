package app.ailiaili.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class AudioQualityTest {
    @Test
    fun labelsKnownAudioTracks() {
        assertEquals("64K", audioQnLabel(30216))
        assertEquals("132K", audioQnLabel(30232))
        assertEquals("192K", audioQnLabel(30280))
        assertEquals("杜比全景声", audioQnLabel(30250))
        assertEquals("Hi-Res 无损", audioQnLabel(30251))
        assertEquals("音质", audioQnLabel(0))
    }
}
