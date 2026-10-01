package eu.kanade.tachiyomi.ui.player.utils

import tachiyomi.core.common.preference.Preference

fun <T> Preference<T>.deleteAndGet(): T {
    delete()
    return get()
}
