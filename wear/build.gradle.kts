plugins {
    alias(libs.plugins.android.application)
}

// Wear OS companion — a single tile that shows the best HUNT and FISH scores on
// the Watch 7. Reuses the shared :engine module so it scores identically to the phone.
android {
    namespace = "com.kairos.wear"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.kairos"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":engine"))
    implementation(libs.androidx.wear.tiles)
    implementation(libs.androidx.wear.protolayout)
    implementation(libs.androidx.wear.protolayout.material)
    implementation(libs.androidx.concurrent.futures)
}
