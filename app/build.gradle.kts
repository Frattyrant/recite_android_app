import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val releaseSigningPropertiesFile = rootProject.file("key.properties")
val releaseSigningProperties = Properties().apply {
    if (releaseSigningPropertiesFile.isFile) {
        releaseSigningPropertiesFile.inputStream().use(::load)
    }
}

fun releaseSigningValue(propertyName: String, environmentName: String): String? =
    releaseSigningProperties.getProperty(propertyName)
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: System.getenv(environmentName)?.trim()?.takeIf(String::isNotEmpty)

val releaseStoreFile = releaseSigningValue("storeFile", "MIEARN_KEYSTORE_PATH")
val releaseStorePassword = releaseSigningValue("storePassword", "MIEARN_KEYSTORE_PASSWORD")
val releaseKeyAlias = releaseSigningValue("keyAlias", "MIEARN_KEY_ALIAS")
val releaseKeyPassword = releaseSigningValue("keyPassword", "MIEARN_KEY_PASSWORD")
val releaseSigningValues = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
)
val releaseSigningConfigured = releaseSigningValues.all { !it.isNullOrBlank() }
check(releaseSigningValues.all { it.isNullOrBlank() } || releaseSigningConfigured) {
    "Release signing is partially configured. Provide all MIEARN signing values."
}

android {
    namespace = "com.miearn.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.miearn.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 4
        versionName = "2.1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        jniLibs.useLegacyPackaging = false
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    val releaseSigningConfig = if (releaseSigningConfigured) {
        signingConfigs.create("release") {
            storeFile = rootProject.file(checkNotNull(releaseStoreFile))
            storePassword = checkNotNull(releaseStorePassword)
            keyAlias = checkNotNull(releaseKeyAlias)
            keyPassword = checkNotNull(releaseKeyPassword)
        }
    } else {
        null
    }

    buildTypes {
        debug {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            releaseSigningConfig?.let { signingConfig = it }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.navigation3:navigation3-runtime:1.1.3")
    implementation("androidx.navigation3:navigation3-ui:1.1.3")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    implementation("androidx.datastore:datastore-preferences:1.2.0")
    implementation("androidx.media3:media3-exoplayer:1.10.1")
    implementation("androidx.work:work-runtime:2.11.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("androidx.room:room-testing:2.8.4")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("androidx.work:work-testing:2.11.2")
    testImplementation("org.robolectric:robolectric:4.16.1")

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

fun registerApkSizeGate(
    taskName: String,
    assembleTask: String,
    relativeApk: String,
    maxBytes: Long,
) = tasks.register(taskName) {
    group = "verification"
    dependsOn(assembleTask)
    doLast {
        val apk = layout.buildDirectory.file(relativeApk).get().asFile
        check(apk.isFile) { "APK was not produced: $apk" }
        check(apk.length() <= maxBytes) {
            "${apk.name} is ${apk.length()} bytes; limit is $maxBytes bytes"
        }
    }
}

registerApkSizeGate(
    "verifyDebugApkSize",
    "assembleDebug",
    "outputs/apk/debug/app-debug.apk",
    65_000_000L,
)
registerApkSizeGate(
    "verifyReleaseApkSize",
    "assembleRelease",
    if (releaseSigningConfigured) {
        "outputs/apk/release/app-release.apk"
    } else {
        "outputs/apk/release/app-release-unsigned.apk"
    },
    55_000_000L,
)
