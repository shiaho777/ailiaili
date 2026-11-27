package app.ailiaili.core.model

fun isNewerVersion(candidate: String, current: String): Boolean {
    fun parts(version: String): List<Int> = version.trim().removePrefix("v").substringBefore('-')
        .split('.').map { it.trim().toIntOrNull() ?: 0 }

    val candidateParts = parts(candidate)
    val currentParts = parts(current)
    for (index in 0 until maxOf(candidateParts.size, currentParts.size)) {
        val candidatePart = candidateParts.getOrElse(index) { 0 }
        val currentPart = currentParts.getOrElse(index) { 0 }
        if (candidatePart != currentPart) return candidatePart > currentPart
    }
    return false
}
