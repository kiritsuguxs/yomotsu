package eu.kanade.tachiyomi.animeextension.pt.saimotv

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import kotlin.system.exitProcess

class DeepLinkActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent?.data?.encodedPath
        if (!path.isNullOrEmpty() && (path.startsWith("/filme/") || path.startsWith("/serie/"))) {
            val searchIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", "${SaimoTV.PREFIX_PATH_SEARCH}$path")
                putExtra("filter", packageName)
            }
            try {
                startActivity(searchIntent)
            } catch (_: ActivityNotFoundException) {
            }
        }
        finish()
        exitProcess(0)
    }
}
