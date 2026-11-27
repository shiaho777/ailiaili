package app.ailiaili.core.data

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchHistoryTest {
    @Test
    fun removesOnlyTheChosenQueryAndPersistsTheOrder() {
        val directory = Files.createTempDirectory("search-history").toFile()
        try {
            val file = AppFile(directory.resolve("search-history.json").absolutePath)
            file.writeTextAtomic("""["Compose","KOTLIN","kotlin","Android"]""")
            val history = SearchHistory(file).apply { load() }
            assertEquals(listOf("Compose", "KOTLIN", "Android"), history.items.value)

            history.add("Kotlin")
            history.remove("compose")

            assertEquals(listOf("Kotlin", "Android"), SearchHistory(file).apply { load() }.items.value)
        } finally {
            directory.deleteRecursively()
        }
    }
}
