package app.ailiaili.core.data

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DynamicSeenStoreTest {
    @Test
    fun persistsBaselinesAcrossReload() {
        val dir = Files.createTempDirectory("dynamic-seen").toFile()
        val file = AppFile(File(dir, "dynamic-seen.json").absolutePath)
        val store = DynamicSeenStore(file)
        store.load()
        assertNull(store.seen(1))

        store.put(1, "BV1")
        store.put(2, "BV2")
        store.put(3, "  ")
        store.flush()

        val reloaded = DynamicSeenStore(file).apply { load() }
        assertEquals("BV1", reloaded.seen(1))
        assertEquals("BV2", reloaded.seen(2))
        assertNull(reloaded.seen(3))

        reloaded.put(1, "BV9")
        reloaded.flush()
        val again = DynamicSeenStore(file).apply { load() }
        assertEquals("BV9", again.seen(1))
    }
}
