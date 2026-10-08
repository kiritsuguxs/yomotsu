package eu.kanade.tachiyomi.data.profile

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class ProfilePreferences(
    private val preferenceStore: PreferenceStore = Injekt.get(),
    private val context: Application = Injekt.get()
) {
    private val usernamePref = preferenceStore.getString("yomotsu_profile_username", "Veterano Yomotsu")
    private val equippedTitleIdPref = preferenceStore.getString("yomotsu_profile_equipped_title_id", "")

    private val avatarTypePref = preferenceStore.getString("yomotsu_profile_avatar_type", "preset") // "preset" or "custom"
    private val avatarPresetPref = preferenceStore.getString("yomotsu_profile_avatar_preset", "preset_yomotsu")
    private val avatarCustomPathPref = preferenceStore.getString("yomotsu_profile_avatar_custom_path", "")
    private val avatarCustomBase64Pref = preferenceStore.getString("yomotsu_profile_avatar_custom_base64", "")

    private val bannerTypePref = preferenceStore.getString("yomotsu_profile_banner_type", "preset") // "preset" or "custom"
    private val bannerPresetPref = preferenceStore.getString("yomotsu_profile_banner_preset", "banner_abyss")
    private val bannerCustomPathPref = preferenceStore.getString("yomotsu_profile_banner_custom_path", "")
    private val bannerCustomBase64Pref = preferenceStore.getString("yomotsu_profile_banner_custom_base64", "")

    private val notifiedAchievementsPref = preferenceStore.getStringSet("yomotsu_profile_notified_achievements", emptySet())

    init {
        migrateLegacyPreferences()
    }

    private fun migrateLegacyPreferences() {
        try {
            val legacy = context.getSharedPreferences("yomotsu_profile_prefs", Context.MODE_PRIVATE)
            if (legacy.all.isEmpty()) return

            if (legacy.contains("username") && usernamePref.get() == "Veterano Yomotsu") {
                val name = legacy.getString("username", null)
                if (!name.isNullOrBlank()) usernamePref.set(name)
            }
            if (legacy.contains("equipped_title_id") && equippedTitleIdPref.get().isBlank()) {
                val title = legacy.getString("equipped_title_id", null)
                if (!title.isNullOrBlank()) equippedTitleIdPref.set(title)
            }
            if (legacy.contains("avatar_uri") && avatarCustomPathPref.get().isBlank()) {
                val uri = legacy.getString("avatar_uri", null)
                if (!uri.isNullOrBlank()) {
                    setAvatarCustom(uri)
                }
            }
            if (legacy.contains("banner_uri") && bannerCustomPathPref.get().isBlank()) {
                val uri = legacy.getString("banner_uri", null)
                if (!uri.isNullOrBlank()) {
                    setBannerCustom(uri)
                }
            }
            if (legacy.contains("notified_achievements") && notifiedAchievementsPref.get().isEmpty()) {
                val set = legacy.getStringSet("notified_achievements", null)
                if (!set.isNullOrEmpty()) {
                    notifiedAchievementsPref.set(set)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getUsername(): String = usernamePref.get().ifBlank { "Veterano Yomotsu" }
    fun setUsername(name: String) = usernamePref.set(name)

    fun getEquippedTitleId(): String? = equippedTitleIdPref.get().takeIf { it.isNotBlank() }
    fun setEquippedTitleId(id: String) = equippedTitleIdPref.set(id)

    fun getAvatarType(): String = avatarTypePref.get()
    fun getAvatarPreset(): String = avatarPresetPref.get().ifBlank { "preset_yomotsu" }

    fun setAvatarPreset(presetId: String) {
        avatarTypePref.set("preset")
        avatarPresetPref.set(presetId)
    }

    fun getAvatarUri(): String? {
        if (avatarTypePref.get() == "preset") return null
        val localPath = avatarCustomPathPref.get()
        if (localPath.isNotBlank() && File(localPath).exists()) {
            return localPath
        }
        val base64 = avatarCustomBase64Pref.get()
        if (base64.isNotBlank()) {
            val restored = restoreBase64ToFile(base64, "avatar_restored_${System.currentTimeMillis()}.jpg")
            if (restored != null) {
                avatarCustomPathPref.set(restored.absolutePath)
                return restored.absolutePath
            }
        }
        return localPath.takeIf { it.isNotBlank() }
    }

    fun setAvatarCustom(filePath: String, base64: String? = null) {
        avatarTypePref.set("custom")
        avatarCustomPathPref.set(filePath)
        val encoded = base64 ?: compressFileToBase64(File(filePath), maxDimension = 400, quality = 80)
        if (encoded != null) {
            avatarCustomBase64Pref.set(encoded)
        }
    }

    fun setAvatarUri(uriString: String?) {
        if (uriString == null) {
            avatarTypePref.set("preset")
            avatarPresetPref.set("preset_yomotsu")
        } else {
            setAvatarCustom(uriString)
        }
    }

    fun getBannerType(): String = bannerTypePref.get()
    fun getBannerPreset(): String = bannerPresetPref.get().ifBlank { "banner_abyss" }

    fun setBannerPreset(presetId: String) {
        bannerTypePref.set("preset")
        bannerPresetPref.set(presetId)
    }

    fun getBannerUri(): String? {
        if (bannerTypePref.get() == "preset") return null
        val localPath = bannerCustomPathPref.get()
        if (localPath.isNotBlank() && File(localPath).exists()) {
            return localPath
        }
        val base64 = bannerCustomBase64Pref.get()
        if (base64.isNotBlank()) {
            val restored = restoreBase64ToFile(base64, "banner_restored_${System.currentTimeMillis()}.jpg")
            if (restored != null) {
                bannerCustomPathPref.set(restored.absolutePath)
                return restored.absolutePath
            }
        }
        return localPath.takeIf { it.isNotBlank() }
    }

    fun setBannerCustom(filePath: String, base64: String? = null) {
        bannerTypePref.set("custom")
        bannerCustomPathPref.set(filePath)
        val encoded = base64 ?: compressFileToBase64(File(filePath), maxDimension = 800, quality = 75)
        if (encoded != null) {
            bannerCustomBase64Pref.set(encoded)
        }
    }

    fun setBannerUri(uriString: String?) {
        if (uriString == null) {
            bannerTypePref.set("preset")
            bannerPresetPref.set("banner_abyss")
        } else {
            setBannerCustom(uriString)
        }
    }

    fun getNotifiedAchievements(): Set<String> = notifiedAchievementsPref.get()
    fun setNotifiedAchievements(ids: Set<String>) = notifiedAchievementsPref.set(ids)

    fun compressFileToBase64(file: File, maxDimension: Int, quality: Int): String? {
        return try {
            if (!file.exists()) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)

            var sampleSize = 1
            while (bounds.outWidth / sampleSize > maxDimension * 2 || bounds.outHeight / sampleSize > maxDimension * 2) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return null

            val maxSide = maxOf(bitmap.width, bitmap.height)
            val scale = if (maxSide > maxDimension) maxDimension.toFloat() / maxSide else 1f
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                bitmap
            }

            val bos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, bos)
            val bytes = bos.toByteArray()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun restoreBase64ToFile(base64: String, filename: String): File? {
        return try {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            val dir = File(context.filesDir, "profile_images")
            if (!dir.exists()) dir.mkdirs()
            val dest = File(dir, filename)
            FileOutputStream(dest).use { it.write(bytes) }
            dest
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
