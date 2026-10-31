package eu.kanade.translation.detection

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process

object DbnetProcess {
    private fun currentProcessName(context: Context): String? {
        return if (Build.VERSION.SDK_INT >= 28) {
            Application.getProcessName()
        } else {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            manager.runningAppProcesses?.firstOrNull { it.pid == Process.myPid() }?.processName
        }
    }

    fun isWorker(context: Context): Boolean {
        val name = currentProcessName(context)
        return name == "${context.packageName}:dbnet" || name == "${context.packageName}:inpaint"
    }

    fun isInpaintWorker(context: Context): Boolean {
        return currentProcessName(context) == "${context.packageName}:inpaint"
    }
}
