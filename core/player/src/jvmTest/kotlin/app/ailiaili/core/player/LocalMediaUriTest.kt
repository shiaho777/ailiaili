package app.ailiaili.core.player

import java.net.URI
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalMediaUriTest {
    @Test
    fun localPathUsesAFileMrlThatVlcRecognizes() {
        val path = Path.of("离线 样本.mp4").toAbsolutePath()
        val mrl = localMediaUri(path.toString())

        assertTrue(mrl.startsWith("file:///"))
        assertTrue("%20" in mrl)
        assertEquals(path, Path.of(URI.create(mrl)))
    }
}
