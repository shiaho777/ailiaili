package app.ailiaili

import app.ailiaili.core.model.isNewerVersion
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionCompareTest {
    @Test
    fun newerPatchIsUpdate() {
        assertTrue(isNewerVersion("1.4.1", "1.4.0"))
        assertTrue(isNewerVersion("1.10.0", "1.9.9"))
        assertTrue(isNewerVersion("2.0.0", "1.99.99"))
    }

    @Test
    fun sameOrOlderIsNotUpdate() {
        assertFalse(isNewerVersion("1.4.0", "1.4.0"))
        // 开发版比 latest 新 / release 回滚：不能弹「发现新版本」推荐降级。
        assertFalse(isNewerVersion("1.3.0", "1.4.0"))
        assertFalse(isNewerVersion("1.4.0", "1.4.1"))
    }

    @Test
    fun missingSegmentsPadZero() {
        assertFalse(isNewerVersion("1.4", "1.4.0"))
        assertTrue(isNewerVersion("1.4.1", "1.4"))
        assertTrue(isNewerVersion("v1.5.0", "1.4.9"))
    }

    @Test
    fun garbageSegmentsDoNotCrash() {
        assertFalse(isNewerVersion("", "1.4.0"))
        assertFalse(isNewerVersion("abc", "1.4.0"))
        assertTrue(isNewerVersion("1.4.1-beta", "1.4.0"))
    }
}
