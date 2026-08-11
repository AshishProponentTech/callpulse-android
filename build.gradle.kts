// Kotlin support is built into AGP 9 — applying org.jetbrains.kotlin.android is an error there.
// The Kotlin compiler *plugins* (compose, serialization) are still applied explicitly.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
