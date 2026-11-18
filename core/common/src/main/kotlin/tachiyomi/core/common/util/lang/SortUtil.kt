package tachiyomi.core.common.util.lang

import java.text.CollationKey
import java.text.Collator
import java.util.Locale

private val threadLocalCollator = object : ThreadLocal<Collator>() {
    override fun initialValue(): Collator {
        val locale = Locale.getDefault()
        return Collator.getInstance(locale).apply {
            strength = Collator.PRIMARY
        }
    }
}

fun String.compareToWithCollator(other: String): Int {
    return threadLocalCollator.get()!!.compare(this, other)
}

fun String.toCollationKey(): CollationKey {
    return threadLocalCollator.get()!!.getCollationKey(this)
}
