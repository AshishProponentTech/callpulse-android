plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * Release signing (§18.1). Credentials come from ~/.gradle/gradle.properties or local.properties,
 * never from the repository. When they are absent — as on a fresh clone or CI without secrets —
 * the release build falls back to the debug key so the build still verifies. It is deliberately
 * loud about that, because an APK signed with the debug key must never reach an employee.
 */
val storeFilePath: String? = providers.gradleProperty("CALLPULSE_STORE_FILE").orNull

android {
    namespace = "com.proponenttech.callpulse"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.proponenttech.callpulse"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()

        // §18.2 — monotonically increasing, never reused. Bump on every build handed to anyone.
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (storeFilePath != null) {
            create("release") {
                storeFile = file(storeFilePath)
                storePassword = providers.gradleProperty("CALLPULSE_STORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("CALLPULSE_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("CALLPULSE_KEY_PASSWORD").get()
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (storeFilePath != null) {
                signingConfigs.getByName("release")
            } else {
                logger.warn(
                    "CALLPULSE_STORE_FILE is not set - signing the release build with the DEBUG key. " +
                        "This APK must not be distributed. See section 18.1.",
                )
                signingConfigs.getByName("debug")
            }
        }
    }

    // Two flavors so a QA phone can hold staging and production side by side (§10).
    flavorDimensions += "env"
    productFlavors {
        create("staging") {
            dimension = "env"
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            // The LAN address of the development machine. See DEVIATIONS.md D-005 - this is
            // also the only flavor permitted to speak cleartext HTTP.
            buildConfigField("String", "API_BASE_URL", "\"http://192.168.1.20:8000/api/v1/\"")
        }
        create("production") {
            dimension = "env"
            buildConfigField("String", "API_BASE_URL", "\"https://pm.proponenttech.com/api/v1/\"")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        // Decision #9 asks for JDK 17. Gradle runs on Studio's bundled JBR, but the bytecode
        // this produces targets 17.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core-common"))
    implementation(project(":core-ui"))
    implementation(project(":core-data"))
    implementation(project(":core-network"))
    implementation(project(":feature-auth"))
    implementation(project(":feature-capture"))
    implementation(project(":feature-review"))
    implementation(project(":feature-day"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.ui.tooling.preview)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.timber)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
