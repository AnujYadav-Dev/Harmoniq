import java.util.Properties

plugins {
    alias(libs.plugins.android.app)
    alias(libs.plugins.android.kotlin)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Global Search secrets come from the environment or local.properties, never from committed files.
// A build-type specific key (e.g. HARMONIQ_SERVER_URL_CANARY) wins over the shared one.
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

fun secret(name: String): String = System.getenv(name) ?: localProperties.getProperty(name) ?: ""

fun buildTypeSecret(name: String, buildType: String): String =
    secret("${name}_${buildType.uppercase()}").ifEmpty { secret(name) }

// public file describing the default Global Search server; editing it on GitHub reaches every
// installed app without a rebuild. Override with HARMONIQ_REMOTE_CONFIG_URL(_<BUILDTYPE>), or set it
// to "none" to build without it (the built-in values above are then the only default).
val defaultRemoteConfigUrl = "https://raw.githubusercontent.com/AnujYadav-Dev/Harmoniq/main/config/global-search.json"

fun remoteConfigUrl(buildType: String): String =
    buildTypeSecret("HARMONIQ_REMOTE_CONFIG_URL", buildType)
        .ifEmpty { defaultRemoteConfigUrl }
        .let { if (it.equals("none", ignoreCase = true)) "" else it }

fun buildConfigString(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "dev.anujyadav.harmoniq"
    compileSdk = libs.versions.compile.sdk.get().toInt()

    defaultConfig {
        applicationId = "dev.anujyadav.harmoniq"
        minSdk = libs.versions.min.sdk.get().toInt()
        targetSdk = libs.versions.target.sdk.get().toInt()

        versionCode = 4
        versionName = "1.3.0"
        versionName = System.getenv("APP_VERSION_NAME") ?: versionName

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        register("release") {
            storeFile = System.getenv("SIGNING_KEYSTORE_FILE")?.let { rootProject.file(it) }
            storePassword = System.getenv("SIGNING_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("SIGNING_KEY_ALIAS")
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
        create("nightly") {
            initWith(getByName("release"))
        }
        create("canary") {
            initWith(getByName("release"))
            applicationIdSuffix = ".canary"
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"

            // For Debug Purpose 
            // isMinifyEnabled = true
            // isShrinkResources = true

            // proguardFiles(
            //     getDefaultProguardFile("proguard-android-optimize.txt"),
            //     "proguard-rules.pro"
            // )
        }
    }

    buildTypes.configureEach {
        listOf("HARMONIQ_SERVER_URL", "HARMONIQ_APP_TOKEN").forEach { key ->
            buildConfigField("String", key, buildConfigString(buildTypeSecret(key, name)))
        }
        buildConfigField("String", "HARMONIQ_REMOTE_CONFIG_URL", buildConfigString(remoteConfigUrl(name)))
    }

    splits {
        abi {
            isEnable = true
            isUniversalApk = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes.add("/META-INF/{AL2.0,LGPL2.1}")
        }
    }

    lint {
        // lifecycle 2.9.x (via android-youtube-player) ships this check built for a newer lint;
        // it crashes AGP 8.7's lint. The app has no LiveData, so nothing is lost. Drop after an AGP upgrade.
        disable += "NullSafeMutableLiveData"
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    testOptions {
        // Robolectric (worker tests) needs the merged resources and manifest
        unitTests.isIncludeAndroidResources = true
        // plain JVM tests reach android.util.Log through the app Logger
        unitTests.isReturnDefaultValues = true
        unitTests.all {
            it.useJUnitPlatform()
        }
    }

    room {
        schemaDirectory("$projectDir/room-schemas")
    }

    // MigrationTestHelper reads the exported schemas as assets. Robolectric unit tests only see
    // the tested variant's merged assets (not the test source set's), so debug builds carry them
    sourceSets {
        getByName("debug").assets.srcDir("$projectDir/room-schemas")
    }
}

dependencies {
    implementation(project(":metaphony"))
    implementation(libs.activity.compose)
    implementation(libs.coil)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.material3)
    implementation(libs.compose.navigation)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.core)
    implementation(libs.core.splashscreen)
    implementation(libs.fuzzywuzzy)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.lifecycle.runtime)
    implementation(libs.media)
    implementation(libs.okhttp3)
    implementation(libs.reorderable)
    implementation(libs.work.runtime)
    implementation(libs.youtube.player)
    ksp(libs.room.compiler)
    implementation(libs.room.ktx)
    implementation(libs.room.runtime)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.okhttp3.mockwebserver)
    testImplementation(libs.work.testing)
    testImplementation(libs.room.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.junit)
    // runs the JUnit 4 based Robolectric tests on the JUnit 5 platform
    testRuntimeOnly(libs.junit.vintage)
}
