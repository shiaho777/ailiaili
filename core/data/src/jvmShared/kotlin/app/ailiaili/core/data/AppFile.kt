package app.ailiaili.core.data

expect class AppFile {
    constructor(path: String)
    constructor(parent: AppFile, child: String)

    val name: String
    val absolutePath: String
    val parentFile: AppFile?

    fun exists(): Boolean
    fun isFile(): Boolean
    fun mkdirs()
    fun readText(): String
    fun writeText(text: String)

    /** 先写临时文件再原子替换，避免进程中断留下半个 JSON。 */
    fun writeTextAtomic(text: String)
    fun writeBytes(bytes: ByteArray)
    fun listFiles(): List<AppFile>
    fun walkFiles(): List<AppFile>
    fun deleteRecursively()
    fun lastModified(): Long
    fun length(): Long
}
