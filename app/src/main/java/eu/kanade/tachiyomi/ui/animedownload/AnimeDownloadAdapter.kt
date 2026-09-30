package eu.kanade.tachiyomi.ui.animedownload

import android.view.MenuItem
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.kanade.presentation.theme.colorscheme.AndroidViewColorScheme

/**
 * Adapter storing a list of downloads.
 *
 * @param downloadItemListener Listener called when an item of the list is released.
 */
class AnimeDownloadAdapter(
    val downloadItemListener: DownloadItemListener,
    // KMK -->
    val colorScheme: AndroidViewColorScheme,
    // KMK <--
) : FlexibleAdapter<AbstractFlexibleItem<*>>(
    null,
    downloadItemListener,
    true,
) {

    override fun shouldMove(fromPosition: Int, toPosition: Int): Boolean {
        // Don't let sub-items changing group
        return getHeaderOf(getItem(fromPosition)) == getHeaderOf(getItem(toPosition))
    }

    interface DownloadItemListener {
        fun onItemReleased(position: Int)
        fun onMenuItemClick(position: Int, menuItem: MenuItem)
    }
}
