-keep class eu.kanade.tachiyomi.source.model.** { public protected *; }
-keep class eu.kanade.tachiyomi.source.online.** { public protected *; }
-keep class eu.kanade.tachiyomi.source.** extends eu.kanade.tachiyomi.source.Source { public protected *; }
-keep class eu.kanade.tachiyomi.animesource.** { *; }
-keep interface eu.kanade.tachiyomi.animesource.** { *; }
-keep class * implements eu.kanade.tachiyomi.animesource.AnimeSource { *; }

-keep,allowoptimization class eu.kanade.tachiyomi.util.JsoupExtensionsKt { public protected *; }
