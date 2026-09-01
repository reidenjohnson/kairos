plugins {
    alias(libs.plugins.android.library)
}

// Pure scoring engine — no UI, no Android framework use beyond java.time.
// Shared by the phone (:app) and the Wear tile (:wear) so both score identically.
android {
    namespace = "com.kairos.engine"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 30
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    testImplementation(libs.junit)
}
