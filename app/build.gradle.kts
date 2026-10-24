plugins {
    id("com.android.application")
}

android {
    namespace = "vn.phuong.iptvplayer"
    compileSdk = 36

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "vn.phuong.iptvplayer"
        minSdk = 23
        targetSdk = 36
        versionCode = 161
        versionName = "1.10.145"

        val ttKey = (project.findProperty("tiktokClientKey") as String?)
            ?: System.getenv("TIKTOK_CLIENT_KEY") ?: ""
        val ttApi = (project.findProperty("tiktokApiBaseUrl") as String?)
            ?: System.getenv("TIKTOK_API_BASE_URL") ?: ""
        fun quoteBuildConfig(value: String): String =
            "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        buildConfigField("String", "TIKTOK_CLIENT_KEY", quoteBuildConfig(ttKey))
        buildConfigField("String", "TIKTOK_API_BASE_URL", quoteBuildConfig(ttApi))
        buildConfigField("String", "TIKTOK_REDIRECT_URI", quoteBuildConfig("https://open-platform.tiktokapis.com/callback"))

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["applicationClass"] = "vn.phuong.iptvplayer.MobileNm7Application"

        // SmartTube legacy modules have a separate internal dimension.
        // Mobile consumes only the stable SmartTube runtime.
        missingDimensionStrategy("default", "ststable")

    }

    // This project branch is the Mobile product only. CI validation run.
    // Do not create a TV flavor or package any TV-only implementation here.
    flavorDimensions += "device"
    productFlavors {
        create("mobile") {
            dimension = "device"
            manifestPlaceholders["vlcFallbackEnabled"] = "false"
            manifestPlaceholders["applicationClass"] = "vn.phuong.iptvplayer.MobileNm7Application"
            if (project.findProperty("mobileAbiSplits") != "true"
                    && project.findProperty("includeEmulatorAbis") != "true") {
                ndk {
                    abiFilters += listOf("arm64-v8a", "armeabi-v7a")
                }
            }
        }
    }

    if (project.findProperty("mobileAbiSplits") == "true") {
        splits {
            abi {
                isEnable = true
                reset()
                include("armeabi-v7a", "arm64-v8a")
                isUniversalApk = false
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions { unitTests.isIncludeAndroidResources = true }

    lint {
        disable += setOf("HardcodedText", "SetTextI18n", "MissingTranslation", "LockedOrientationActivity")
    }
}

dependencies {
    val media3Version = "1.11.0"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    implementation("androidx.media3:media3-exoplayer-dash:$media3Version")
    implementation("androidx.media3:media3-exoplayer-smoothstreaming:$media3Version")
    implementation("androidx.media3:media3-exoplayer-rtsp:$media3Version")
    implementation("androidx.media3:media3-datasource-rtmp:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-datasource-okhttp:$media3Version")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Official TikTok Login Kit and core OpenSDK. Pin the SDK version for reproducible builds.
    implementation("com.tiktok.open.sdk:tiktok-open-sdk-core:2.3.1")
    implementation("com.tiktok.open.sdk:tiktok-open-sdk-auth:2.3.1")
    // ViewPager2 provides a native vertical feed for the user's public TikTok videos.
    implementation("androidx.viewpager2:viewpager2:1.1.0")

    // SmartTube's DroidApplication extends AndroidX MultiDexApplication.
    // Keep the dependency direct so the Mobile app compiler can resolve that superclass.
    implementation("androidx.multidex:multidex:2.0.1")

    // SmartTube is the only additional runtime for this Mobile product.
    // Keep TV/VLC implementation completely out of the Mobile dependency graph.
    add("mobileImplementation", project(":smarttube"))

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("org.json:json:20240303")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}
