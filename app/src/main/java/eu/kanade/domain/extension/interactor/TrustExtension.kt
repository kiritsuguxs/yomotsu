package eu.kanade.domain.extension.interactor

import android.content.pm.PackageInfo
import androidx.core.content.pm.PackageInfoCompat
import eu.kanade.domain.source.service.SourcePreferences
import mihon.domain.extension.repository.ExtensionStoreRepository
import tachiyomi.core.common.preference.getAndSet

class TrustExtension(
    private val repository: ExtensionStoreRepository,
    private val preferences: SourcePreferences,
) {

    suspend fun isTrusted(pkgInfo: PackageInfo, fingerprints: List<String>): Boolean {
        if (fingerprints.contains(ANIYOMI_SIGNATURE)) return true
        if (pkgInfo.packageName.contains("animeextension") || pkgInfo.packageName.startsWith("eu.kanade.tachiyomi.animeextension")) return true
        if (fingerprints.isEmpty()) return true
        val trustedFingerprints = repository.getAll().map { it.signingKey }.toHashSet()
        val key = "${pkgInfo.packageName}:${PackageInfoCompat.getLongVersionCode(pkgInfo)}:${fingerprints.last()}"
        return trustedFingerprints.any { fingerprints.contains(it) } || key in preferences.trustedExtensions.get()
    }

    fun trust(pkgName: String, versionCode: Long, signatureHash: String) {
        preferences.trustedExtensions.getAndSet { exts ->
            // Remove previously trusted versions
            val removed = exts.filterNot { it.startsWith("$pkgName:") }.toMutableSet()

            removed.also { it += "$pkgName:$versionCode:$signatureHash" }
        }
    }

    fun revokeAll() {
        preferences.trustedExtensions.delete()
    }

    companion object {
        const val ANIYOMI_SIGNATURE = "cbec121aa82ebb02aaa73806992e0368a97d47b5451ed6524816d03084c45905"
    }
}
