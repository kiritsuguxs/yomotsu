sed -i '/scope.launch(kotlinx.coroutines.Dispatchers.IO) {/,+7c\
        try {\n            initAnimeExtensions()\n        } catch (e: Throwable) {\n            logcat(LogPriority.ERROR, e) { "Failed to initialize anime extensions" }\n            _isInitialized.value = true\n        }' app/src/main/java/eu/kanade/tachiyomi/extension/anime/AnimeExtensionManager.kt
