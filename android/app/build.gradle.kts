import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.roborazzi)
}

// Release signing for sideloaded builds. Passwords live in keystore/release-keystore.pw
// (gitignored). If the file is missing, release builds fall back to debug signing so
// `assembleRelease` still works on fresh clones.
val keystoreProps = Properties().apply {
    val pwFile = rootProject.file("keystore/release-keystore.pw")
    if (pwFile.exists()) pwFile.inputStream().use { load(it) }
}
val releaseStorePassword = keystoreProps.getProperty("storePassword", "")

android {
    namespace = "com.example"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.paperadda.app"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseStorePassword.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file("keystore/paperadda-release.jks")
                storePassword = releaseStorePassword
                keyAlias = "paperadda"
                keyPassword = releaseStorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    // Robolectric requires reflective access that JDK 17+ blocks by default.
    // These flags apply both to ./gradlew test and to Android Studio test runs.
    tasks.withType<Test> {
        jvmArgs(
            "--add-opens", "java.base/java.io=ALL-UNNAMED",
            "--add-opens", "java.base/java.lang=ALL-UNNAMED",
            "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
            "--add-opens", "java.base/java.util=ALL-UNNAMED",
            "--add-opens", "java.base/java.text=ALL-UNNAMED",
            "--add-opens", "java.base/java.nio=ALL-UNNAMED",
            "--add-opens", "java.desktop/java.awt.font=ALL-UNNAMED",
            "--add-exports", "java.base/jdk.internal.access=ALL-UNNAMED",
            "--add-opens", "java.base/jdk.internal.access=ALL-UNNAMED"
        )
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material.icons.extended)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.compose.ui.test.manifest)
    testImplementation(libs.roborazzi)

    debugImplementation(libs.compose.ui.test.manifest)
}
