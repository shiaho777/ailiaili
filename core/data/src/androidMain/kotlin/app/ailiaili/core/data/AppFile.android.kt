package app.ailiaili.core.data

import java.io.File

actual class AppFile {
    private val file: File

    actual constructor(path: String) {
        file = File(path)
    }

    actual constructor(parent: AppFile, child: String) {
        file = File(parent.file, child)
    }

    actual val name: String get() = file.name
    actual val absolutePath: String get() = file.absolutePath
    actual val parentFile: AppFile? get() = file.parentFile?.let { AppFile(it.absolutePath) }

    actual fun exists(): Boolean = file.exists()
    actual fun isFile(): Boolean = file.isFile
    actual fun mkdirs() {
        file.mkdirs()
    }
    actual fun readText(): String = file.readText()
    actual fun writeText(text: String) {
        file.parentFile?.mkdirs()
        file.writeText(text)
    }
    actual fun writeTextAtomic(text: String) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        runCatching {
            java.nio.file.Files.move(
                tmp.toPath(),
                file.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
            )
        }.onFailure {
            file.writeText(text)
            tmp.delete()
        }
    }
    actual fun writeBytes(bytes: ByteArray) {
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
    }
    actual fun listFiles(): List<AppFile> = file.listFiles()?.map { AppFile(it.absolutePath) }.orEmpty()
    actual fun walkFiles(): List<AppFile> =
        file.walkTopDown().filter { it.isFile }.map { AppFile(it.absolutePath) }.toList()
    actual fun deleteRecursively() {
        file.deleteRecursively()
    }
    actual fun lastModified(): Long = file.lastModified()
    actual fun length(): Long = file.length()
}
