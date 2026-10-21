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
    // The android.jar used for unit tests ships org.json as empty stubs; pull in
    // the real implementation so parsing code (WeatherRepository, NwsWeather) can
    // be tested against captured API responses.
    testImplementation("org.json:json:20240303")
}
