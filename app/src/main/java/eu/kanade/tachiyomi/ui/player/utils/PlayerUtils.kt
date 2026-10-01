package eu.kanade.tachiyomi.ui.player.utils

object PlayerUtils {
    fun prettyTime(time: Int): String {
        val hours = time / 3600
        val minutes = (time % 3600) / 60
        val seconds = time % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
    fun prettyTime(time: Int, isInverted: Boolean): String {
        return if (isInverted) "-" + prettyTime(time) else prettyTime(time)
    }
}
