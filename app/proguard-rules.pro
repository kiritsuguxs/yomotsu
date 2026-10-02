-dontobfuscate

-keep,allowoptimization class eu.kanade.**
-keep,allowoptimization class tachiyomi.**
-keep,allowoptimization class mihon.**

# Keep common dependencies used in extensions
-keep,allowoptimization class androidx.preference.** { public protected *; }
-keep,allowoptimization class kotlin.** { public protected *; }
-keep,allowoptimization class kotlinx.coroutines.** { public protected *; }
-keep,allowoptimization class kotlinx.serialization.** { public protected *; }
-keep,allowoptimization class kotlin.time.** { public protected *; }
-keep,allowoptimization class okhttp3.** { public protected *; }
-keep,allowoptimization class okio.** { public protected *; }
-keep,allowoptimization class org.jsoup.** { public protected *; }
-keep,allowoptimization class rx.** { public protected *; }
-keep class app.cash.quickjs.** { *; }
-keep interface eu.kanade.tachiyomi.extension.novel.runtime.NovelJsRuntime$NativeApi { *; }
-keepclassmembers interface eu.kanade.tachiyomi.extension.novel.runtime.NovelJsRuntime$NativeApi { *; }
-keep class * implements eu.kanade.tachiyomi.extension.novel.runtime.NovelJsRuntime$NativeApi { *; }
-keepclassmembers class * implements eu.kanade.tachiyomi.extension.novel.runtime.NovelJsRuntime$NativeApi { *; }
-keep class eu.kanade.tachiyomi.extension.novel.runtime.** { *; }
-keepclassmembers class eu.kanade.tachiyomi.extension.novel.runtime.** { *; }
-keep,allowoptimization class uy.kohesive.injekt.** { public protected *; }
-keep,allowoptimization class com.squareup.zstd.** { public protected *; }

# ML Kit OCR uses runtime component discovery. Keep its implementations in release builds.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_common.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_bundled_common.** { *; }

# PaddleOCR uses ONNX Runtime and SDK classes through its inference pipeline.
-keep class com.paddle.ocr.** { *; }
-keep class ai.onnxruntime.** { *; }

# From extensions-lib
-keep,allowoptimization class eu.kanade.tachiyomi.network.interceptor.RateLimitInterceptorKt { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.network.interceptor.SpecificHostRateLimitInterceptorKt { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.network.NetworkHelper { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.network.OkHttpExtensionsKt { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.network.RequestsKt { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.AppInfo { public protected *; }
-keep class eu.kanade.tachiyomi.animesource.** { *; }
-keep interface eu.kanade.tachiyomi.animesource.** { *; }
-keep class * implements eu.kanade.tachiyomi.animesource.AnimeSource { *; }

-keepclassmembers class * implements java.io.Serializable {
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

##---------------Begin: proguard configuration for RxJava 1.x  ----------
-dontwarn sun.misc.**

-keepclassmembers class rx.internal.util.unsafe.*ArrayQueue*Field* {
   long producerIndex;
   long consumerIndex;
}

-keepclassmembers class rx.internal.util.unsafe.BaseLinkedQueueProducerNodeRef {
    rx.internal.util.atomic.LinkedQueueNode producerNode;
}

-keepclassmembers class rx.internal.util.unsafe.BaseLinkedQueueConsumerNodeRef {
    rx.internal.util.atomic.LinkedQueueNode consumerNode;
}

-dontnote rx.internal.util.PlatformDependent
##---------------End: proguard configuration for RxJava 1.x  ----------

##---------------Begin: proguard configuration for okhttp  ----------
-keepclasseswithmembers class okhttp3.MultipartBody$Builder { *; }
##---------------End: proguard configuration for okhttp  ----------

##---------------Begin: proguard configuration for kotlinx.serialization  ----------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.** # core serialization annotations

# kotlinx-serialization-json specific. Add this if you have java.lang.NoClassDefFoundError kotlinx.serialization.json.JsonObjectSerializer
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class eu.kanade.**$$serializer { *; }
-keepclassmembers class eu.kanade.** {
    *** Companion;
}
-keepclasseswithmembers class eu.kanade.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep class kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.** {
    <methods>;
}
##---------------End: proguard configuration for kotlinx.serialization  ----------

# XmlUtil
-keep public enum nl.adaptivity.xmlutil.EventType { *; }

# Firebase
-keep class com.google.firebase.installations.** { *; }
-keep interface com.google.firebase.installations.** { *; }

# KotlinX Datetime
-keep,allowoptimization class kotlinx.datetime.** { public protected *; }

# Methods called by Shizuku only
-keepclassmembers class mihon.app.shizuku.ShellInterface {
    public <init>();
    public void destroy();
}

# Detector JNI symbols are called only from the disposable :dbnet process.
-keep class eu.kanade.translation.detection.DbnetNativeBackend { *; }

# TDLib JNI callbacks
-keep class org.drinkless.tdlib.** { *; }

# Keep all native methods across the application
-keepclasseswithmembernames class * {
    native <methods>;
}

# MPV Android Lib JNI and Callbacks
-keep class is.xyz.mpv.** { *; }
-keepclassmembers class is.xyz.mpv.** { *; }
-keep interface is.xyz.mpv.** { *; }
-dontwarn is.xyz.mpv.**

# Player JNI observers and components
-keep class eu.kanade.tachiyomi.ui.player.PlayerObserver { *; }
-keepclassmembers class eu.kanade.tachiyomi.ui.player.PlayerObserver { *; }
-keep class eu.kanade.tachiyomi.ui.player.AniyomiMPVView { *; }
-keepclassmembers class eu.kanade.tachiyomi.ui.player.AniyomiMPVView { *; }
-keep class eu.kanade.tachiyomi.ui.player.PlayerActivity {
    void onObserverEvent(...);
    void event(...);
    void onTrackLoadedFailure(...);
}

# FFmpegKit JNI and Callbacks
-keep class com.arthenica.ffmpegkit.** { *; }
-keepclassmembers class com.arthenica.ffmpegkit.** { *; }
-dontwarn com.arthenica.ffmpegkit.**

# Anime Download & Player models
-keep class eu.kanade.tachiyomi.data.animedownload.** { *; }
-keepclassmembers class eu.kanade.tachiyomi.data.animedownload.** { *; }
-keep class animiru.feature.mpvfiles.** { *; }
-keepclassmembers class animiru.feature.mpvfiles.** { *; }
-keep class eu.kanade.tachiyomi.ui.player.ChapterNode { *; }
-keepclassmembers class eu.kanade.tachiyomi.ui.player.ChapterNode { *; }
-keep class eu.kanade.tachiyomi.ui.player.ChapterNode$$serializer { *; }
-keep class eu.kanade.tachiyomi.ui.player.TrackNode { *; }
-keepclassmembers class eu.kanade.tachiyomi.ui.player.TrackNode { *; }
-keep class eu.kanade.tachiyomi.ui.player.TrackNode$$serializer { *; }
