package tachiyomi.domain.release.interactor

import tachiyomi.domain.release.model.Release
import tachiyomi.domain.release.service.ReleaseService

class GetApplicationRelease(
    private val service: ReleaseService,
) {
    suspend fun await(arguments: Arguments): Result {
        val release = service.latest(arguments) ?: return Result.NoNewUpdate

        // Check if latest version is different from current version
        val isNewVersion = isNewVersion(
            arguments.isPreview,
            arguments.commitCount,
            arguments.versionName,
            release.version,
        )
        return when {
            isNewVersion -> Result.NewUpdate(release)
            else -> Result.NoNewUpdate
        }
    }

    private fun isNewVersion(
        isPreview: Boolean,
        commitCount: Int,
        versionName: String,
        versionTag: String,
    ): Boolean {
        // Removes prefixes like "r" or "v"
        val newVersion = versionTag.replace("[^\\d.]".toRegex(), "")
        return if (isPreview) {
            // Preview builds: based on releases tagged as "r1234", falling back to SemVer if tagged as "v0.22.2"
            newVersion.toIntOrNull()?.let { it > commitCount } ?: compareSemVer(versionName, versionTag)
        } else {
            // Release builds: tagged as something like "v0.1.2" or "v0.21.5"
            compareSemVer(versionName, versionTag)
        }
    }

    private fun compareSemVer(versionName: String, versionTag: String): Boolean {
        val cleanOldVersion = versionName.substringBefore('-').replace("[^\\d.]".toRegex(), "")
        val cleanNewVersion = versionTag.substringBefore('-').replace("[^\\d.]".toRegex(), "")

        val newSemVer = cleanNewVersion.split(".").filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }
        val oldSemVer = cleanOldVersion.split(".").filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }

        val minSize = minOf(oldSemVer.size, newSemVer.size)
        for (index in 0 until minSize) {
            if (newSemVer[index] > oldSemVer[index]) {
                return true
            }
            if (newSemVer[index] < oldSemVer[index]) {
                return false
            }
        }

        return newSemVer.size > oldSemVer.size
    }

    data class Arguments(
        val isFoss: Boolean,
        val isPreview: Boolean,
        val commitCount: Int,
        val versionName: String,
        val repository: String,
        val forceCheck: Boolean = false,
    )

    sealed interface Result {
        data class NewUpdate(val release: Release) : Result
        data object NoNewUpdate : Result
        data object OsTooOld : Result
    }
}
