package app.ailiaili.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class QualityLabelTest {
    @Test
    fun labelsHighQualityTiers() {
        assertEquals("8K", qnLabel(127))
        assertEquals("杜比视界", qnLabel(126))
        assertEquals("HDR", qnLabel(125))
        assertEquals("4K", qnLabel(120))
        assertEquals("1080P", qnLabel(80))
    }
}
