package eu.kanade.tachiyomi.ui.player

import android.content.Context
import animiru.feature.mpvfiles.MpvConfig
import `is`.xyz.mpv.MPV
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.File

enum class Anime4KMode(val title: String) {
    Off("Desativado"),
    ModeA("Modo A (Restauração)"),
    ModeB("Modo B (Suavização)"),
    ModeC("Modo C (Fidelidade)"),
    ;

    fun getShaderList(): List<String> = when (this) {
        Off -> emptyList()
        ModeA -> listOf(
            "Anime4K_Clamp_Highlights.glsl",
            "Anime4K_Restore_CNN_M.glsl",
            "Anime4K_Upscale_CNN_x2_M.glsl",
            "Anime4K_AutoDownscalePre_x2.glsl",
            "Anime4K_AutoDownscalePre_x4.glsl",
            "Anime4K_Upscale_CNN_x2_S.glsl",
        )
        ModeB -> listOf(
            "Anime4K_Clamp_Highlights.glsl",
            "Anime4K_Restore_CNN_Soft_M.glsl",
            "Anime4K_Upscale_CNN_x2_M.glsl",
            "Anime4K_AutoDownscalePre_x2.glsl",
            "Anime4K_AutoDownscalePre_x4.glsl",
            "Anime4K_Upscale_CNN_x2_S.glsl",
        )
        ModeC -> listOf(
            "Anime4K_Clamp_Highlights.glsl",
            "Anime4K_Upscale_Denoise_CNN_x2_M.glsl",
            "Anime4K_AutoDownscalePre_x2.glsl",
            "Anime4K_AutoDownscalePre_x4.glsl",
            "Anime4K_Upscale_CNN_x2_S.glsl",
        )
    }

    companion object {
        fun apply(mpv: MPV?, context: Context, mode: Anime4KMode) {
            if (mpv == null) return
            try {
                mpv.command("change-list", "glsl-shaders", "clr", "")
                val shaderFiles = mode.getShaderList()
                if (shaderFiles.isEmpty()) {
                    mpv.command("show-text", "Anime4K: Desativado", "2000")
                    logcat(LogPriority.INFO) { "Anime4K cleared (Off)" }
                    return
                }

                val shadersDir = File(context.filesDir, "${MpvConfig.MPV_DIR}/${MpvConfig.MPV_SHADERS_DIR}")
                if (!shadersDir.exists() || shadersDir.list().isNullOrEmpty()) {
                    copyAssetShadersFallback(context, shadersDir)
                }

                for (shader in shaderFiles) {
                    val shaderFile = File(shadersDir, shader)
                    if (shaderFile.exists()) {
                        mpv.command("change-list", "glsl-shaders", "append", shaderFile.absolutePath)
                    }
                }
                mpv.command("show-text", "Anime4K: ${mode.title}", "2000")
                logcat(LogPriority.INFO) { "Anime4K applied: ${mode.name}" }
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e) { "Failed to apply Anime4K shader: ${e.message}" }
            }
        }

        private fun copyAssetShadersFallback(context: Context, destDir: File) {
            runCatching {
                destDir.mkdirs()
                val files = context.assets.list("shaders") ?: return
                for (file in files) {
                    val outFile = File(destDir, file)
                    if (outFile.exists() && outFile.length() > 0) continue
                    context.assets.open("shaders/$file").use { input ->
                        outFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }.onFailure {
                logcat(LogPriority.ERROR, it) { "Fallback shader copy failed: ${it.message}" }
            }
        }
    }
}
