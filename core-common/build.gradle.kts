plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.proponenttech.callpulse.core.common"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    // §13.4 — E.164 normalisation, default region IN. Shared by capture and review.
    api(libs.libphonenumber.android)
    api(libs.timber)

    testImplementation(libs.junit)
}
