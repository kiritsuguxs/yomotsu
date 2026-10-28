plugins {
    id("com.android.application") version "8.2.2"
    kotlin("android") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
}

val extVersionCode = 1
val libVersion = "1.6"

val calculatedVersionCode = libVersion.split(".")
    .joinToString("") { it.padStart(2, '0') }
    .toInt() * 1000 + extVersionCode

android {
    namespace = "eu.kanade.tachiyomi.animeextension.pt.saimotv"
    compileSdk = 34

    defaultConfig {
        applicationId = "eu.kanade.tachiyomi.animeextension.pt.saimotv"
        minSdk = 21
        targetSdk = 34
        versionCode = calculatedVersionCode
        versionName = "$libVersion.$extVersionCode"

        manifestPlaceholders["appName"] = "Aniyomi: Saimo TV"
        manifestPlaceholders["extClass"] = ".SaimoTV"
        manifestPlaceholders["extVersionCode"] = extVersionCode.toString()
        manifestPlaceholders["extFactory"] = ""
        manifestPlaceholders["nsfw"] = "0"
        manifestPlaceholders["sourceUrl"] = "https://saimotv.com"
        manifestPlaceholders["isNsfw"] = "false"
        manifestPlaceholders["libVersion"] = libVersion
    }

    val tempKeystore = file(System.getenv("RUNNER_TEMP") ?: "").resolve("signing.keystore")
    val hasSigning = tempKeystore.exists()

    if (hasSigning) {
        signingConfigs {
            create("release") {
                storeFile = tempKeystore
                storePassword = System.getenv("STOREPASSWORD")
                keyAlias = System.getenv("KEYALIAS")
                keyPassword = System.getenv("KEYPASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (hasSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs += listOf(
            "-Xskip-metadata-version-check",
            "-Xskip-prerelease-check",
        )
    }
}

dependencies {
    compileOnly("com.github.komikku-app:aniyomi-extensions-lib:f5961b5dfb")
    compileOnly("com.squareup.okhttp3:okhttp:4.12.0")
    compileOnly("org.jsoup:jsoup:1.17.2")
    compileOnly("io.reactivex:rxjava:1.3.8")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}
