package com.github.biltudas1.sequence.util

object VersionUtils {
    /**
     * Extracts the major version from a string like "1.0", "1.0.1", "1.0a1", etc.
     * Only detects the part before the first dot or before any letters if no dot.
     */
    fun extractMajorVersion(version: String): Int? {
        val firstPart = version.split('.')[0]
        // Remove any non-numeric characters from the start
        val majorString = firstPart.takeWhile { it.isDigit() }
        return majorString.toIntOrNull()
    }
}
