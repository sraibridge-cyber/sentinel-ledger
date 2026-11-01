plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// The core logic and the chain code live in shared folders so the tests and the app use the same code.
android {
    namespace = "io.sentinel.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.sentinel.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.2.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    sourceSets {
        getByName("main") { java.srcDirs("../core/src/main/java", "../sdk/src/main/java") }
        getByName("test") { java.srcDirs("../core/src/test/java") }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
