package tachiyomi.source.local.image

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.util.storage.DiskUtil
import tachiyomi.core.common.storage.nameWithoutExtension
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.source.local.io.LocalAnimeSourceFileSystem
import java.io.InputStream

private const val DEFAULT_COVER_NAME = "cover.jpg"

class LocalAnimeCoverManager(
    private val context: Context,
    private val fileSystem: LocalAnimeSourceFileSystem,
) {

    fun find(animeUrl: String): UniFile? {
        val files = fileSystem.getFilesInAnimeDirectory(animeUrl)
        // First look for cover.*, poster.* or folder.*
        return files
            .filter {
                it.isFile && (
                    it.nameWithoutExtension.equals("cover", ignoreCase = true) ||
                        it.nameWithoutExtension.equals("poster", ignoreCase = true) ||
                        it.nameWithoutExtension.equals("folder", ignoreCase = true)
                )
            }
            .firstOrNull { ImageUtil.isImage(it.name) { it.openInputStream() } }
            // Fallback: any image file in the directory
            ?: files
                .filter { it.isFile }
                .firstOrNull { ImageUtil.isImage(it.name) { it.openInputStream() } }
    }

    fun update(
        anime: SAnime,
        inputStream: InputStream,
    ): UniFile? {
        val targetFile = update(anime.url, inputStream)
        if (targetFile != null) {
            anime.thumbnail_url = targetFile.uri.toString()
        }
        return targetFile
    }

    fun update(
        animeUrl: String,
        inputStream: InputStream,
    ): UniFile? {
        val directory = fileSystem.getAnimeDirectory(animeUrl)
        if (directory == null) {
            inputStream.close()
            return null
        }

        val targetFile = find(animeUrl) ?: directory.createFile(DEFAULT_COVER_NAME)!!

        inputStream.use { input ->
            targetFile.openOutputStream().use { output ->
                input.copyTo(output)
            }
        }

        DiskUtil.createNoMediaFile(directory, context)

        return targetFile
    }
}
