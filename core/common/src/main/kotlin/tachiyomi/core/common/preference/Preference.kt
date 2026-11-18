package tachiyomi.core.common.preference

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface Preference<T> {

    fun key(): String

    fun get(): T

    fun set(value: T)

    fun isSet(): Boolean

    fun delete()

    fun defaultValue(): T

    fun changes(): Flow<T>

    fun stateIn(scope: CoroutineScope): StateFlow<T>

    companion object {
        /**
         * A preference that should not be exposed in places like backups without user consent.
         */
        fun isPrivate(key: String): Boolean {
            return key.startsWith(PRIVATE_PREFIX) || key in SENSITIVE_KEYS
        }
        fun privateKey(key: String): String {
            return "$PRIVATE_PREFIX$key"
        }

        /**
         * A preference used for internal app state that isn't really a user preference
         * and therefore should not be in places like backups.
         */
        fun isAppState(key: String): Boolean {
            return key.startsWith(APP_STATE_PREFIX)
        }
        fun appStateKey(key: String): String {
            return "$APP_STATE_PREFIX$key"
        }

        private const val APP_STATE_PREFIX = "__APP_STATE_"
        private const val PRIVATE_PREFIX = "__PRIVATE_"
        private val SENSITIVE_KEYS = setOf(
            "translation_gemini_api_key",
            "translation_groq_api_key",
            "translation_engine_api_key",
            "deepl_api_key",
            "pref_telegram_bot_token",
            "pref_telegram_chat_id",
        )
    }
}

inline fun <reified T, R : T> Preference<T>.getAndSet(crossinline block: (T) -> R) = set(
    block(get()),
)

operator fun <T> Preference<Set<T>>.plusAssign(item: T) {
    set(get() + item)
}

operator fun <T> Preference<Set<T>>.plusAssign(items: Iterable<T>) {
    set(get() + items)
}

operator fun <T> Preference<Set<T>>.minusAssign(item: T) {
    set(get() - item)
}

fun Preference<Boolean>.toggle(): Boolean {
    set(!get())
    return get()
}
