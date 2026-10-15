// Top-level build file where you can add configuration options common to all sub-projects/modules.
// Note: AGP 9 has built-in Kotlin support, so org.jetbrains.kotlin.android
// must NOT be applied (see https://kotl.in/gradle/agp-built-in-kotlin).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.roborazzi) apply false
}
