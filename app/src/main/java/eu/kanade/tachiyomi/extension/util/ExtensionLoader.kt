package eu.kanade.tachiyomi.extension.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import eu.kanade.domain.extension.interactor.TrustExtension
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.LoadResult
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.util.lang.Hash
import eu.kanade.tachiyomi.util.storage.copyAndSetReadOnlyTo
import eu.kanade.tachiyomi.util.system.ChildFirstPathClassLoader
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.injectLazy
import java.io.File

/**
 * Class that handles the loading of the extensions. Supports two kinds of extensions:
 *
 * 1. Shared extension: This extension is installed to the system with package
 * installer, so other variants of Tachiyomi and its forks can also use this extension.
 *
 * 2. Private extension: This extension is put inside private data directory of the
 * running app, so this extension can only be used by the running app and not shared
 * with other apps.
 *
 * When both kinds of extensions are installed with a same package name, shared
 * extension will be used unless the version codes are different. In that case the
 * one with higher version code will be used.
 */
internal object ExtensionLoader {

    private val preferences: SourcePreferences by injectLazy()
    private val trustExtension: TrustExtension by injectLazy()
    private val loadNsfwSource by lazy {
        preferences.showNsfwSource.get()
    }

    private const val EXTENSION_FEATURE = "tachiyomi.extension"
    private const val ANIME_EXTENSION_FEATURE = "tachiyomi.animeextension"
    private const val METADATA_SOURCE_CLASS = "tachiyomi.extension.class"
    private const val METADATA_SOURCE_FACTORY = "tachiyomi.extension.factory"
    private const val METADATA_NSFW = "tachiyomi.extension.nsfw"

    private const val METADATA_NAME = "tachiyomix.name"
    private const val METADATA_EXTENSION_LIB = "tachiyomix.extensionLib"
    private const val METADATA_CONTENT_WARNING = "tachiyomix.contentWarning"

    private val SUPPORTED_LIB_VERSIONS = listOf(1.4, 1.6)

    @Suppress("DEPRECATION")
    private val PACKAGE_FLAGS = PackageManager.GET_CONFIGURATIONS or
        PackageManager.GET_META_DATA or
        PackageManager.GET_SIGNATURES or
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0)

    private const val PRIVATE_EXTENSION_EXTENSION = "ext"

    private fun getPrivateExtensionDir(context: Context) = File(context.filesDir, "exts")

    private fun getPackageArchiveInfoCompat(pkgManager: PackageManager, path: String): PackageInfo? {
        val pkg = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pkgManager.getPackageArchiveInfo(
                    path,
                    PackageManager.PackageInfoFlags.of(PACKAGE_FLAGS.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                pkgManager.getPackageArchiveInfo(path, PACKAGE_FLAGS)
            } ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pkgManager.getPackageArchiveInfo(
                    path,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                pkgManager.getPackageArchiveInfo(path, PackageManager.GET_META_DATA)
            }
        } catch (e: Throwable) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pkgManager.getPackageArchiveInfo(
                        path,
                        PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    pkgManager.getPackageArchiveInfo(path, PackageManager.GET_META_DATA)
                }
            } catch (e2: Throwable) {
                logcat(LogPriority.ERROR, e2) { "Failed to get package archive info for $path" }
                null
            }
        }
        pkg?.applicationInfo?.fixBasePaths(path)
        return pkg
    }

    fun installPrivateExtensionFile(context: Context, file: File): Boolean {
        val extension = getPackageArchiveInfoCompat(context.packageManager, file.absolutePath)
            ?.takeIf { isPackageAnExtension(it) } ?: return false
        val currentExtension = getExtensionPackageInfoFromPkgName(context, extension.packageName)

        if (currentExtension != null) {
            if (PackageInfoCompat.getLongVersionCode(extension) <
                PackageInfoCompat.getLongVersionCode(currentExtension)
            ) {
                logcat(LogPriority.ERROR) { "Installed extension version is higher. Downgrading is not allowed." }
                return false
            }

            val extensionSignatures = getSignatures(extension)
            val currentSignatures = getSignatures(currentExtension)
            if (!extensionSignatures.isNullOrEmpty() && !currentSignatures.isNullOrEmpty()) {
                if (!extensionSignatures.containsAll(currentSignatures)) {
                    logcat(LogPriority.ERROR) { "Installed extension signature is not matched." }
                    return false
                }
            }
        }

        val privateDir = getPrivateExtensionDir(context).apply { mkdirs() }
        val target = File(privateDir, "${extension.packageName}.$PRIVATE_EXTENSION_EXTENSION")
        return try {
            target.delete()
            file.copyAndSetReadOnlyTo(target, overwrite = true)
            if (currentExtension != null) {
                ExtensionInstallReceiver.notifyReplaced(context, extension.packageName)
            } else {
                ExtensionInstallReceiver.notifyAdded(context, extension.packageName)
            }
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to copy extension file." }
            target.delete()
            false
        }
    }

    fun uninstallPrivateExtension(context: Context, pkgName: String) {
        File(getPrivateExtensionDir(context), "$pkgName.$PRIVATE_EXTENSION_EXTENSION").delete()
    }

    /**
     * Return a list of all the available extensions initialized concurrently.
     *
     * @param context The application context.
     */
    fun loadExtensions(context: Context): List<LoadResult> {
        val pkgManager = context.packageManager

        val installedPkgs = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pkgManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(PACKAGE_FLAGS.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pkgManager.getInstalledPackages(PACKAGE_FLAGS)
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Failed to get installed packages" }
            emptyList()
        }

        val sharedExtPkgs = installedPkgs
            .asSequence()
            .filter { isPackageAnExtension(it) }
            .map { ExtensionInfo(packageInfo = it, isShared = true) }

        val privateExtPkgs = try {
            getPrivateExtensionDir(context)
                .listFiles()
                ?.asSequence()
                ?.filter { it.isFile && it.extension == PRIVATE_EXTENSION_EXTENSION }
                ?.mapNotNull {
                    // Just in case, since Android 14+ requires them to be read-only
                    if (it.canWrite()) {
                        it.setReadOnly()
                    }

                    val path = it.absolutePath
                    getPackageArchiveInfoCompat(pkgManager, path)
                }
                ?.filter { isPackageAnExtension(it) }
                ?.map { ExtensionInfo(packageInfo = it, isShared = false) }
                ?: emptySequence()
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Failed to get private extension packages" }
            emptySequence()
        }

        val extPkgs = (sharedExtPkgs + privateExtPkgs)
            // Remove duplicates. Shared takes priority than private by default
            .distinctBy { it.packageInfo.packageName }
            // Compare version number
            .mapNotNull { sharedPkg ->
                val privatePkg = privateExtPkgs
                    .singleOrNull { it.packageInfo.packageName == sharedPkg.packageInfo.packageName }
                selectExtensionPackage(sharedPkg, privatePkg)
            }
            .toList()

        if (extPkgs.isEmpty()) return emptyList()

        // Load each extension concurrently and wait for completion
        return runBlocking {
            val deferred = extPkgs.map { extensionInfo ->
                async {
                    try {
                        loadExtension(context, extensionInfo)
                    } catch (e: Throwable) {
                        logcat(LogPriority.ERROR, e) { "Failed to load extension ${extensionInfo.packageInfo.packageName}" }
                        LoadResult.Error
                    }
                }
            }
            deferred.awaitAll()
        }
    }

    /**
     * Attempts to load an extension from the given package name. It checks if the extension
     * contains the required feature flag before trying to load it.
     */
    suspend fun loadExtensionFromPkgName(context: Context, pkgName: String): LoadResult {
        val extensionPackage = getExtensionInfoFromPkgName(context, pkgName)
        if (extensionPackage == null) {
            logcat(LogPriority.ERROR) { "Extension package is not found ($pkgName)" }
            return LoadResult.Error
        }
        return loadExtension(context, extensionPackage)
    }

    fun getExtensionPackageInfoFromPkgName(context: Context, pkgName: String): PackageInfo? {
        return getExtensionInfoFromPkgName(context, pkgName)?.packageInfo
    }

    private fun getExtensionInfoFromPkgName(context: Context, pkgName: String): ExtensionInfo? {
        val privateExtensionFile = File(getPrivateExtensionDir(context), "$pkgName.$PRIVATE_EXTENSION_EXTENSION")
        val privatePkg = if (privateExtensionFile.isFile) {
            getPackageArchiveInfoCompat(context.packageManager, privateExtensionFile.absolutePath)
                ?.takeIf { isPackageAnExtension(it) }
                ?.let {
                    ExtensionInfo(
                        packageInfo = it,
                        isShared = false,
                    )
                }
        } else {
            null
        }

        val sharedPkg = try {
            val pkg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    pkgName,
                    PackageManager.PackageInfoFlags.of(PACKAGE_FLAGS.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(pkgName, PACKAGE_FLAGS)
            }
            pkg.takeIf { isPackageAnExtension(it) }
                ?.let {
                    ExtensionInfo(
                        packageInfo = it,
                        isShared = true,
                    )
                }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }

        return selectExtensionPackage(sharedPkg, privatePkg)
    }

    /**
     * Loads an extension
     *
     * @param context The application context.
     * @param extensionInfo The extension to load.
     */
    private suspend fun loadExtension(context: Context, extensionInfo: ExtensionInfo): LoadResult {
        return try {
            val pkgManager = context.packageManager
            val pkgInfo = extensionInfo.packageInfo
            val appInfo = pkgInfo.applicationInfo ?: return LoadResult.Error
            val pkgName = pkgInfo.packageName ?: return LoadResult.Error
            val isAnime = extensionInfo.isAnime

            val extName = try {
                appInfo.metaData?.getString(METADATA_NAME)
                    ?: pkgManager.getApplicationLabel(appInfo).toString()
                        .substringAfter("Aniyomi: ")
                        .substringAfter("Tachiyomi: ")
            } catch (e: Throwable) {
                pkgName.substringAfterLast('.')
            }
            val versionName = pkgInfo.versionName
            val versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo)

            if (versionName.isNullOrEmpty()) {
                logcat(LogPriority.WARN) { "Missing versionName for extension $extName" }
                return LoadResult.Error
            }

            // Validate lib version
            val libVersion = appInfo.metaData?.getFloat(METADATA_EXTENSION_LIB)
                ?.takeUnless { it == 0.0f }
                ?.toString()
                ?.toDoubleOrNull()
                ?: appInfo.metaData?.getInt("aniyomi.animeextension.libVersion")?.toDouble()
                ?: appInfo.metaData?.getInt("tachiyomi.animeextension.libVersion")?.toDouble()
                ?: versionName.substringBeforeLast('.').toDoubleOrNull()
            val isLibSupported = if (isAnime) {
                true
            } else {
                libVersion != null && libVersion in SUPPORTED_LIB_VERSIONS
            }
            if (!isLibSupported) {
                logcat(LogPriority.WARN) {
                    "Lib version is $libVersion, while only version(s) ${SUPPORTED_LIB_VERSIONS.joinToString()} are supported"
                }
                return LoadResult.Error
            }

            val signatures = getSignatures(pkgInfo)
            if (signatures.isNullOrEmpty()) {
                if (!isAnime) {
                    logcat(LogPriority.WARN) { "Package $pkgName isn't signed" }
                    return LoadResult.Error
                }
            } else if (!trustExtension.isTrusted(pkgInfo, signatures)) {
                val extension = Extension.Untrusted(
                    extName,
                    pkgName,
                    versionName,
                    versionCode,
                    libVersion ?: 0.0,
                    signatures.lastOrNull() ?: "",
                    isAnime = isAnime,
                )
                logcat(LogPriority.WARN) { "Extension $pkgName isn't trusted" }
                return LoadResult.Untrusted(extension)
            }

            val nsfwKey = if (isAnime) "tachiyomi.animeextension.nsfw" else METADATA_NSFW
            val isNsfw = (appInfo.metaData?.getInt(METADATA_CONTENT_WARNING) ?: 0) > 0 ||
                appInfo.metaData?.getInt(nsfwKey) == 1
            if (!loadNsfwSource && isNsfw) {
                logcat(LogPriority.WARN) { "NSFW extension $pkgName not allowed" }
                return LoadResult.Error
            }

            val classLoader = try {
                ChildFirstPathClassLoader(appInfo.sourceDir, null, context.classLoader)
            } catch (e: Throwable) {
                try {
                    dalvik.system.PathClassLoader(appInfo.sourceDir, null, context.classLoader)
                } catch (e2: Throwable) {
                    logcat(LogPriority.ERROR, e2) { "Extension load error: $extName ($pkgName)" }
                    return LoadResult.Error
                }
            }

            val sourceClassKey = if (isAnime) "tachiyomi.animeextension.class" else METADATA_SOURCE_CLASS
            val sourceFactoryKey = if (isAnime) "tachiyomi.animeextension.factory" else METADATA_SOURCE_FACTORY
            val sourceClassString = appInfo.metaData?.getString(sourceClassKey)
                ?: (if (isAnime) appInfo.metaData?.getString("aniyomi.animeextension.class") else null)
                ?: appInfo.metaData?.getString(METADATA_SOURCE_CLASS)
                ?: appInfo.metaData?.getString(sourceFactoryKey)
                ?: (if (isAnime) appInfo.metaData?.getString("aniyomi.animeextension.factory") else null)
                ?: appInfo.metaData?.getString(METADATA_SOURCE_FACTORY)
            if (sourceClassString == null) {
                logcat(LogPriority.WARN) { "Missing source class metadata for extension $extName ($pkgName)" }
                return LoadResult.Error
            }

            val sources = sourceClassString
                .split(";")
                .map {
                    val sourceClass = it.trim()
                    if (sourceClass.startsWith(".")) {
                        pkgInfo.packageName + sourceClass
                    } else {
                        sourceClass
                    }
                }
                .flatMap {
                    try {
                        when (val obj = Class.forName(it, false, classLoader).getDeclaredConstructor().newInstance()) {
                            is Source -> listOf(obj)
                            is SourceFactory -> obj.createSources()
                            is eu.kanade.tachiyomi.animesource.AnimeSourceFactory -> obj.createSources()
                            else -> throw Exception("Unknown source class type: ${obj.javaClass}")
                        }
                    } catch (e: Throwable) {
                        try {
                            val fallBackClassLoader = dalvik.system.PathClassLoader(appInfo.sourceDir, null, context.classLoader)
                            when (val obj = Class.forName(it, false, fallBackClassLoader).getDeclaredConstructor().newInstance()) {
                                is Source -> listOf(obj)
                                is SourceFactory -> obj.createSources()
                                is eu.kanade.tachiyomi.animesource.AnimeSourceFactory -> obj.createSources()
                                else -> throw Exception("Unknown source class type: ${obj.javaClass}")
                            }
                        } catch (e2: Throwable) {
                            logcat(LogPriority.ERROR, e2) { "Extension load error: $extName ($it)" }
                            return LoadResult.Error
                        }
                    }
                }

            val langs = sources.map { it.lang }.toSet()
            val lang = when (langs.size) {
                0 -> ""
                1 -> langs.first()
                else -> "all"
            }

            val icon = try {
                appInfo.loadIcon(pkgManager)
            } catch (e: Throwable) {
                null
            }

            val extension = Extension.Installed(
                name = extName,
                pkgName = pkgName,
                versionName = versionName,
                versionCode = versionCode,
                libVersion = libVersion ?: 0.0,
                lang = lang,
                isNsfw = isNsfw,
                isAnime = isAnime,
                sources = sources,
                pkgFactory = appInfo.metaData?.getString(sourceFactoryKey) ?: appInfo.metaData?.getString(METADATA_SOURCE_FACTORY),
                icon = icon,
                isShared = extensionInfo.isShared,
            )
            LoadResult.Success(extension)
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Failed to load extension ${extensionInfo.packageInfo.packageName}" }
            LoadResult.Error
        }
    }

    /**
     * Choose which extension package to use based on version code
     *
     * @param shared extension installed to system
     * @param private extension installed to data directory
     */
    private fun selectExtensionPackage(shared: ExtensionInfo?, private: ExtensionInfo?): ExtensionInfo? {
        when {
            private == null && shared != null -> return shared
            shared == null && private != null -> return private
            shared == null && private == null -> return null
        }

        return if (PackageInfoCompat.getLongVersionCode(shared!!.packageInfo) >=
            PackageInfoCompat.getLongVersionCode(private!!.packageInfo)
        ) {
            shared
        } else {
            private
        }
    }

    /**
     * Returns true if the given package is an extension.
     *
     * @param pkgInfo The package info of the application.
     */
    private fun isPackageAnExtension(pkgInfo: PackageInfo): Boolean {
        return pkgInfo.reqFeatures.orEmpty().any { it.name == EXTENSION_FEATURE || it.name == ANIME_EXTENSION_FEATURE } ||
            pkgInfo.packageName.contains("animeextension") ||
            pkgInfo.packageName.startsWith("eu.kanade.tachiyomi.animeextension") ||
            pkgInfo.packageName.startsWith("eu.kanade.tachiyomi.extension")
    }

    /**
     * Returns the signatures of the package or null if it's not signed.
     *
     * @param pkgInfo The package info of the application.
     * @return List SHA256 digest of the signatures
     */
    private fun getSignatures(pkgInfo: PackageInfo): List<String>? {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = pkgInfo.signingInfo
            when {
                signingInfo != null && signingInfo.hasMultipleSigners() -> signingInfo.apkContentsSigners
                signingInfo != null -> signingInfo.signingCertificateHistory
                else -> @Suppress("DEPRECATION") pkgInfo.signatures
            }
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.signatures
        }
        return signatures
            ?.map { Hash.sha256(it.toByteArray()) }
            ?.toList()
    }

    /**
     * On Android 13+ the ApplicationInfo generated by getPackageArchiveInfo doesn't
     * have sourceDir which breaks assets loading (used for getting icon here).
     */
    private fun ApplicationInfo.fixBasePaths(apkPath: String) {
        if (sourceDir == null) {
            sourceDir = apkPath
        }
        if (publicSourceDir == null) {
            publicSourceDir = apkPath
        }
    }

    private data class ExtensionInfo(
        val packageInfo: PackageInfo,
        val isShared: Boolean,
    ) {
        val isAnime: Boolean
            get() = packageInfo.reqFeatures.orEmpty().any { it.name == ANIME_EXTENSION_FEATURE } ||
                packageInfo.packageName.contains("animeextension") ||
                packageInfo.packageName.startsWith("eu.kanade.tachiyomi.animeextension")
    }
}
