package io.nekohasekai.sfa.utils

import java.io.File

/** Path safety checks for profile config files (UI-1c). */
object ProfilePathGuard {
    fun managedConfigsDir(filesDir: File): File = File(filesDir, "configs").canonicalFile

    fun isUnderManagedDir(file: File, configsDir: File): Boolean {
        val canonical =
            try {
                file.canonicalFile
            } catch (_: Exception) {
                return false
            }
        val root =
            try {
                configsDir.canonicalFile
            } catch (_: Exception) {
                return false
            }
        val path = canonical.path
        val prefix = root.path.trimEnd(File.separatorChar) + File.separator
        return path == root.path || path.startsWith(prefix)
    }

    fun pathSharedWithOther(path: String, profileId: Long, otherPaths: Map<Long, String>): Boolean {
        if (path.isBlank()) return false
        val canonical =
            try {
                File(path).canonicalPath
            } catch (_: Exception) {
                path
            }
        return otherPaths.any { (id, other) ->
            if (id == profileId) return@any false
            val otherCanonical =
                try {
                    File(other).canonicalPath
                } catch (_: Exception) {
                    other
                }
            otherCanonical == canonical
        }
    }
}
