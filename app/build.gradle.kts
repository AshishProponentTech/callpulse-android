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
        // 2 = the first build that talks to the live server at proponenttech.in, and the first
        // signed with the real release key. versionCode 1 was debug-signed and pointed at a host
        // that never served this API; it was never distributed.
        versionCode = 2
        versionName = "1.0.0"

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

            /*
             * 10.0.2.2 is the emulator's alias for the host machine's loopback, so an emulator
             * build reaches Laravel without touching the LAN interface - and therefore without
             * needing a Windows Firewall rule at all.
             *
             * A real phone cannot resolve 10.0.2.2; it needs the host's LAN address instead.
             * Override without editing this file:
             *     ./gradlew assembleStagingDebug -PcallpulseApiBase=http://192.168.1.20:8000/api/v1/
             *
             * See DEVIATIONS.md D-005 - staging is the only flavor permitted cleartext HTTP,
             * and network_security_config restricts even that to these specific hosts.
             */
            val apiBase = providers.gradleProperty("callpulseApiBase")
                .getOrElse("http://10.0.2.2:8000/api/v1/")
            buildConfigField("String", "API_BASE_URL", "\"$apiBase\"")
        }
        create("production") {
            dimension = "env"

            /*
             * The live ERP at proponenttech.in serves the CallPulse API and owns the projects
             * and employee accounts the app authenticates against. There is no separate
             * call-tracking backend: routes/api.php in proponent-admin is the server.
             */
            buildConfigField("String", "API_BASE_URL", "\"https://proponenttech.in/api/v1/\"")
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
