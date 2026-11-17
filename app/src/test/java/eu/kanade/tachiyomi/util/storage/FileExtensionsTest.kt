package eu.kanade.tachiyomi.util.storage

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class FileExtensionsTest {

    @Test
    fun `copyAndSetReadOnlyTo copies entire file and marks destination read-only`(@TempDir tempDir: Path) {
        val source = File(tempDir.toFile(), "source.apk")
        val target = File(tempDir.toFile(), "exts/test.ext")
        val payload = ByteArray(16 * 1024) { index -> (index % 251).toByte() }
        source.writeBytes(payload)

        source.copyAndSetReadOnlyTo(target, overwrite = true)

        assertArrayEquals(payload, target.readBytes())
        assertFalse(target.canWrite())
    }

    @Test
    fun `copyAndSetReadOnlyTo overwrites an existing read-only target`(@TempDir tempDir: Path) {
        val source = File(tempDir.toFile(), "source_new.apk")
        val target = File(tempDir.toFile(), "test.ext")
        target.writeText("old-content")
        target.setReadOnly()

        val updated = "updated-extension-payload".toByteArray()
        source.writeBytes(updated)

        source.copyAndSetReadOnlyTo(target, overwrite = true)

        assertArrayEquals(updated, target.readBytes())
        assertFalse(target.canWrite())
    }
}
