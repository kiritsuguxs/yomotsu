package mihon.core.archive

import me.zhanghai.android.libarchive.Archive
import me.zhanghai.android.libarchive.ArchiveEntry
import me.zhanghai.android.libarchive.ArchiveException
import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.concurrent.Volatile
import mihon.core.archive.ArchiveEntry as MihonArchiveEntry

internal class ArchiveInputStream(buffer: Long, size: Long) : InputStream() {
    private val lock = Any()

    @Volatile
    private var isClosed = false

    private val archive = Archive.readNew()

    init {
        try {
            Archive.setCharset(archive, Charsets.UTF_8.name().toByteArray())
            Archive.readSupportFilterAll(archive)
            Archive.readSupportFormatAll(archive)
            Archive.readOpenMemoryUnsafe(archive, buffer, size)
        } catch (e: ArchiveException) {
            close()
            throw e
        }
    }

    private val oneByteBuffer = ByteBuffer.allocateDirect(1)

    override fun read(): Int {
        synchronized(lock) {
            if (isClosed) return -1
            read(oneByteBuffer)
            return if (oneByteBuffer.hasRemaining()) oneByteBuffer.get().toUByte().toInt() else -1
        }
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (off < 0 || len < 0 || len > b.size - off) {
            throw IndexOutOfBoundsException()
        }
        if (len == 0) return 0
        synchronized(lock) {
            if (isClosed) return -1
            if (off == 0 && len == b.size) {
                val buffer = ByteBuffer.wrap(b)
                read(buffer)
                return if (buffer.hasRemaining()) buffer.remaining() else -1
            }
            val scratch = ByteArray(len)
            val buffer = ByteBuffer.wrap(scratch)
            read(buffer)
            val bytesRead = buffer.remaining()
            if (bytesRead <= 0) return -1
            scratch.copyInto(
                destination = b,
                destinationOffset = off,
                startIndex = 0,
                endIndex = bytesRead,
            )
            return bytesRead
        }
    }

    private fun read(buffer: ByteBuffer) {
        buffer.clear()
        Archive.readData(archive, buffer)
        buffer.flip()
    }

    override fun close() {
        synchronized(lock) {
            if (isClosed) return
            isClosed = true
            Archive.readFree(archive)
        }
    }

    fun getNextEntry(): MihonArchiveEntry? {
        return Archive.readNextHeader(archive).takeUnless { it == 0L }?.let { entry ->
            val name = ArchiveEntry.pathnameUtf8(entry) ?: ArchiveEntry.pathname(entry)?.decodeToString() ?: return null
            val isFile = ArchiveEntry.filetype(entry) == ArchiveEntry.AE_IFREG
            MihonArchiveEntry(name, isFile)
        }
    }
}
