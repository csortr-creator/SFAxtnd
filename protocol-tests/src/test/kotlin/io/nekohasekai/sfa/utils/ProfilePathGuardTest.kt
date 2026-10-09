package io.nekohasekai.sfa.utils

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProfilePathGuardTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun managedDirAcceptsOnlyConfigsChildren() {
        val filesDir = tmp.newFolder("files")
        val configs = ProfilePathGuard.managedConfigsDir(filesDir)
        configs.mkdirs()
        val ok = File(configs, "1.json").apply { writeText("{}") }
        assertTrue(ProfilePathGuard.isUnderManagedDir(ok, configs))
        val outside = tmp.newFile("escape.json")
        assertFalse(ProfilePathGuard.isUnderManagedDir(outside, configs))
        val traversal = File(configs, "../escape.json")
        assertFalse(ProfilePathGuard.isUnderManagedDir(traversal, configs))
    }

    @Test
    fun pathSharedDetectsDuplicatePaths() {
        val dir = tmp.newFolder("configs")
        val a = File(dir, "a.json").apply { writeText("1") }
        val b = File(dir, "b.json").apply { writeText("2") }
        assertFalse(
            ProfilePathGuard.pathSharedWithOther(a.path, 1L, mapOf(1L to a.path, 2L to b.path))
        )
        assertTrue(
            ProfilePathGuard.pathSharedWithOther(a.path, 1L, mapOf(1L to a.path, 2L to a.path))
        )
    }
}
