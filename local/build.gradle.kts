plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "id.my.daniza.local"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 27

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

}

dependencies {
    testImplementation(libs.junit)
}