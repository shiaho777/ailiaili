package app.ailiaili.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MentionParserTest {
    @Test
    fun parsesMultipleMentions() {
        assertEquals(listOf("a", "b"), parseMentions("@a @b"))
        assertEquals(listOf("雨霁影清"), parseMentions("回复 @雨霁影清 你说得对"))
        assertEquals(listOf("某人"), parseMentions("（@某人）"))
    }

    @Test
    fun ignoresEmailAddresses() {
        assertEquals(emptyList(), parseMentions("someone@example.com"))
        assertEquals(emptyList(), parseMentions("a_b@example.com"))
        assertEquals(listOf("real"), parseMentions("mail x@y.com @real"))
    }

    @Test
    fun ignoresOverlongNames() {
        assertEquals(emptyList(), parseMentions("@" + "a".repeat(21)))
        assertEquals(listOf("a".repeat(20)), parseMentions("@" + "a".repeat(20)))
    }

    @Test
    fun splitsMentionSegmentsWithAtSign() {
        val parts = splitMentions("你好 @雨霁影清，再见")
        assertEquals(
            listOf(
                MentionSegment("你好 ", false),
                MentionSegment("@雨霁影清", true),
                MentionSegment("，再见", false),
            ),
            parts,
        )
    }

    @Test
    fun emailIsOnePlainSegment() {
        val parts = splitMentions("x@y.com")
        assertEquals(1, parts.size)
        assertTrue(!parts[0].isMention)
        assertEquals("x@y.com", parts[0].text)
    }
}
