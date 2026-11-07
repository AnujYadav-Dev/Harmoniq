plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.android.kotlin)
}

android {
    namespace = "dev.anujyadav.harmoniq.metaphony"
    compileSdk = libs.versions.compile.sdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.min.sdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        externalNativeBuild {
            cmake {
                cppFlags("")
                // 64-bit libraries must load on 16 KB page devices. NDK r28+ does this by default,
                // so this line can go when the project moves to one
                arguments("-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("nightly") {
            initWith(getByName("release"))
        }
        create("canary") {
            initWith(getByName("release"))
        }
    }

    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
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

    // the test songs for the device tests. Not in src/main/assets, which would ship them in the app
    sourceSets {
        getByName("androidTest").assets.srcDir("../app/src/test/resources")
    }

    testOptions {
        androidResources {
            noCompress.addAll(listOf("flac", "ogg", "mp3"))
        }
    }
}

dependencies {
    implementation(libs.core)
    implementation(libs.appcompat)
    implementation(libs.material)

    testImplementation(libs.junit)

    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}