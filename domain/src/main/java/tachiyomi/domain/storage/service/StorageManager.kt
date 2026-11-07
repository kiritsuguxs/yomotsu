package tachiyomi.domain.storage.service

import android.content.Context
import androidx.core.net.toUri
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.util.storage.DiskUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn

class StorageManager(
    private val context: Context,
    storagePreferences: StoragePreferences,
) {

    private val scope = CoroutineScope(Dispatchers.IO)

    private var baseDir: UniFile? = getBaseDir(storagePreferences.baseStorageDirectory.get())
    private val migrationLock = Any()
    @Volatile
    private var migratedUri: String? = null

    private val _changes: Channel<Unit> = Channel(Channel.UNLIMITED)
    val changes = _changes.receiveAsFlow()
        .shareIn(scope, SharingStarted.Lazily, 1)

    init {
        storagePreferences.baseStorageDirectory.changes()
            .distinctUntilChanged()
            .onEach { uri ->
                val isInitial = migratedUri == null
                baseDir = getBaseDir(uri)
                baseDir?.let { parent ->
                    setupDirectories(parent, uri)
                }
                if (!isInitial) {
                    _changes.send(Unit)
                }
            }
            .launchIn(scope)
    }

    private fun setupDirectories(parent: UniFile, uri: String) {
        parent.createDirectory(AUTOMATIC_BACKUPS_PATH)
        parent.createDirectory(LOCAL_SOURCE_PATH)
        parent.createDirectory(LOCAL_ANIMESOURCE_PATH)
        // TachiyomiAT
        parent.createDirectory(TRANSLATION_PATH)
        parent.createDirectory(DOWNLOADS_PATH).also {
            DiskUtil.createNoMediaFile(it, context)
        }
        parent.createDirectory(ANIME_DOWNLOADS_PATH).also {
            DiskUtil.createNoMediaFile(it, context)
        }
        parent.createDirectory(NOVEL_DOWNLOADS_PATH).also {
            DiskUtil.createNoMediaFile(it, context)
        }
        ensureMpvDirectoriesMigrated(parent, uri)
    }

    private fun ensureMpvDirectoriesMigrated(
        parent: UniFile? = baseDir,
        uri: String? = parent?.uri?.toString(),
    ): UniFile? {
        val root = parent ?: return null
        synchronized(migrationLock) {
            val mpvDir = root.createDirectory(MPV_PATH)?.also {
                DiskUtil.createNoMediaFile(it, context)
            } ?: return null

            if (uri != null && migratedUri == uri) {
                return mpvDir
            }

            val subDirs = listOf(SCRIPTS_PATH, SCRIPTS_OPTS_PATH, SHADERS_PATH, FONTS_PATH)
            for (dirName in subDirs) {
                val targetDir = mpvDir.createDirectory(dirName)
                val legacyDir = root.findFile(dirName)?.takeIf { it.isDirectory } ?: continue
                if (targetDir != null) {
                    legacyDir.listFiles()?.forEach { legacyFile ->
                        if (legacyFile.isFile) {
                            val fileName = legacyFile.name ?: return@forEach
                            if (targetDir.findFile(fileName) == null) {
                                runCatching {
                                    targetDir.createFile(fileName)?.let { outFile ->
                                        legacyFile.openInputStream().use { input ->
                                            outFile.openOutputStream().use { output ->
                                                input.copyTo(output)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        runCatching { legacyFile.delete() }
                    }
                }
                runCatching { legacyDir.delete() }
            }

            if (uri != null) {
                migratedUri = uri
            }
            return mpvDir
        }
    }

    private fun getBaseDir(uri: String): UniFile? {
        return UniFile.fromUri(context, uri.toUri())
            .takeIf { it?.exists() == true }
    }

    fun getAutomaticBackupsDirectory(): UniFile? {
        return baseDir?.createDirectory(AUTOMATIC_BACKUPS_PATH)
    }

    fun getDownloadsDirectory(): UniFile? {
        return baseDir?.createDirectory(DOWNLOADS_PATH)
    }

    fun getAnimeDownloadsDirectory(): UniFile? {
        return baseDir?.createDirectory(ANIME_DOWNLOADS_PATH)?.also {
            DiskUtil.createNoMediaFile(it, context)
        }
    }

    fun getNovelDownloadsDirectory(): UniFile? {
        return baseDir?.createDirectory(NOVEL_DOWNLOADS_PATH)?.also {
            DiskUtil.createNoMediaFile(it, context)
        }
    }

    fun getLocalSourceDirectory(): UniFile? {
        return baseDir?.createDirectory(LOCAL_SOURCE_PATH)
    }

    fun getLocalAnimeSourceDirectory(): UniFile? {
        return baseDir?.createDirectory(LOCAL_ANIMESOURCE_PATH)
    }

    // TachiyomiAT
    fun getTranslationsDirectory(): UniFile? {
        return baseDir?.createDirectory(TRANSLATION_PATH)
    }

    fun getMpvDirectory(): UniFile? {
        return ensureMpvDirectoriesMigrated()
    }

    fun getScriptsDirectory(): UniFile? {
        return getMpvDirectory()?.createDirectory(SCRIPTS_PATH)
    }

    fun getScriptOptsDirectory(): UniFile? {
        return getMpvDirectory()?.createDirectory(SCRIPTS_OPTS_PATH)
    }

    fun getShadersDirectory(): UniFile? {
        return getMpvDirectory()?.createDirectory(SHADERS_PATH)
    }

    fun getFontsDirectory(): UniFile? {
        return getMpvDirectory()?.createDirectory(FONTS_PATH)
    }
}

private const val AUTOMATIC_BACKUPS_PATH = "autobackup"
private const val DOWNLOADS_PATH = "downloads"
private const val ANIME_DOWNLOADS_PATH = "animes"
private const val NOVEL_DOWNLOADS_PATH = "novels"
private const val LOCAL_SOURCE_PATH = "local"
private const val LOCAL_ANIMESOURCE_PATH = "localanime"

// TachiyomiAT
private const val TRANSLATION_PATH = "translations"

private const val MPV_PATH = "mpv"
private const val SCRIPTS_PATH = "scripts"
private const val SCRIPTS_OPTS_PATH = "script-opts"
private const val SHADERS_PATH = "shaders"
private const val FONTS_PATH = "fonts"
