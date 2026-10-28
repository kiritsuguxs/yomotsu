package eu.kanade.tachiyomi.extension.pt.blitztoons

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import kotlin.system.exitProcess

class DeepLinkActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent?.data?.encodedPath
        if (!path.isNullOrEmpty() && (path.startsWith("/obra/") || path.startsWith("/ler/"))) {
            val realPath = if (path.startsWith("/ler/")) {
                val slug = path.removePrefix("/ler/").substringBefore("/")
                "/obra/$slug"
            } else {
                path
            }
            val searchIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", "${BlitzToons.PREFIX_PATH_SEARCH}$realPath")
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
