plugins {
    id("com.android.application") version "8.2.2"
    kotlin("android") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"
}

val extVersionCode = 1
val libVersion = "1.6"

val calculatedVersionCode = libVersion.split(".")
    .joinToString("") { it.padStart(2, '0') }
    .toInt() * 1000 + extVersionCode

android {
    namespace = "eu.kanade.tachiyomi.animeextension.pt.animeshentai"
    compileSdk = 34

    defaultConfig {
        applicationId = "eu.kanade.tachiyomi.animeextension.pt.animeshentai"
        minSdk = 21
        targetSdk = 34
        versionCode = calculatedVersionCode
        versionName = "$libVersion.$extVersionCode"

        manifestPlaceholders["appName"] = "Aniyomi: Animes Hentai"
        manifestPlaceholders["extClass"] = ".AnimesHentai"
        manifestPlaceholders["extVersionCode"] = extVersionCode.toString()
        manifestPlaceholders["extFactory"] = ""
        manifestPlaceholders["nsfw"] = "1"
        manifestPlaceholders["sourceUrl"] = "https://animeshentai.biz"
        manifestPlaceholders["isNsfw"] = "true"
        manifestPlaceholders["libVersion"] = libVersion
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    compileOnly("com.github.komikku-app:aniyomi-extensions-lib:f5961b5dfb")
    compileOnly("com.squareup.okhttp3:okhttp:4.12.0")
    compileOnly("org.jsoup:jsoup:1.17.2")
    compileOnly("io.reactivex:rxjava:1.3.8")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}
