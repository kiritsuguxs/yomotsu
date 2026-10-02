package eu.kanade.tachiyomi.util.system

import dalvik.system.PathClassLoader
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.util.Enumeration

/**
 * A parent-last class loader that will try in order:
 * - the system class loader
 * - the child class loader
 * - the parent class loader.
 */
class ChildFirstPathClassLoader(
    private val dexPath: String,
    librarySearchPath: String?,
    parent: ClassLoader,
) : PathClassLoader(dexPath, librarySearchPath, parent) {

    private val systemClassLoader: ClassLoader? = getSystemClassLoader()

    private fun isParentFirst(name: String?): Boolean {
        if (name == null) return false
        return name.startsWith("eu.kanade.tachiyomi.source.") ||
            name.startsWith("eu.kanade.tachiyomi.animesource.") ||
            name.startsWith("eu.kanade.tachiyomi.network.") ||
            name.startsWith("eu.kanade.tachiyomi.util.") ||
            name.startsWith("eu.kanade.tachiyomi.AppInfo") ||
            name.startsWith("eu.kanade.tachiyomi.core.") ||
            name.startsWith("tachiyomi.") ||
            name.startsWith("mihon.") ||
            name.startsWith("kotlin.") ||
            name.startsWith("kotlinx.") ||
            name.startsWith("android.") ||
            name.startsWith("androidx.") ||
            name.startsWith("okhttp3.") ||
            name.startsWith("okio.") ||
            name.startsWith("org.jsoup.") ||
            name.startsWith("rx.") ||
            name.startsWith("uy.kohesive.injekt.")
    }

    override fun loadClass(name: String?, resolve: Boolean): Class<*> {
        var c = findLoadedClass(name)

        if (c == null && isParentFirst(name)) {
            c = try {
                super.loadClass(name, resolve)
            } catch (_: ClassNotFoundException) {
                null
            }
        }

        if (c == null && systemClassLoader != null) {
            try {
                c = systemClassLoader.loadClass(name)
            } catch (_: ClassNotFoundException) {}
        }

        if (c == null) {
            c = try {
                findClass(name)
            } catch (_: ClassNotFoundException) {
                super.loadClass(name, resolve)
            }
        }

        if (resolve) {
            resolveClass(c)
        }

        return c
    }

    override fun getResource(name: String?): URL? {
        if (name == null) return null
        val cleanName = name.removePrefix("/")
        return systemClassLoader?.getResource(cleanName)
            ?: findResource(cleanName)
            ?: findInApk(cleanName)
            ?: super.getResource(cleanName)
    }

    private fun findInApk(name: String): URL? {
        return try {
            val file = java.io.File(dexPath)
            if (!file.exists()) return null
            val zip = java.util.zip.ZipFile(file)
            val entry = zip.getEntry(name)
                ?: zip.getEntry("assets/$name")
                ?: zip.getEntry("res/raw/$name")
            val entryName = entry?.name
            zip.close()
            if (entryName != null) {
                java.net.URI("jar:file:${file.absolutePath}!/$entryName").toURL()
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    override fun getResources(name: String?): Enumeration<URL> {
        val systemUrls = systemClassLoader?.getResources(name)
        val localUrls = findResources(name)
        val parentUrls = parent?.getResources(name)
        val urls = buildList {
            while (systemUrls?.hasMoreElements() == true) {
                add(systemUrls.nextElement())
            }

            while (localUrls?.hasMoreElements() == true) {
                add(localUrls.nextElement())
            }

            while (parentUrls?.hasMoreElements() == true) {
                add(parentUrls.nextElement())
            }
        }

        return object : Enumeration<URL> {
            val iterator = urls.iterator()

            override fun hasMoreElements() = iterator.hasNext()
            override fun nextElement() = iterator.next()
        }
    }

    override fun getResourceAsStream(name: String?): InputStream? {
        return try {
            getResource(name)?.openStream()
        } catch (_: IOException) {
            return null
        }
    }
}
