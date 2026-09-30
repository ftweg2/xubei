plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.ftwegc.myapplication"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.ftwegc.myapplication"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // The app is installed over earlier builds signed with this computer's debug key, so the
        // release build uses the same key (Android only installs an update signed like the
        // installed app). The debug keystore's passwords are the public SDK defaults.
        create("localRelease") {
            storeFile = file(System.getProperty("user.home") + "/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            // AGP 9.2's `optimization { enable = true }` refuses to run without the experimental
            // android.r8.gradual.support flag, so the long-standing switches are used.
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("localRelease")
        }
        // Exactly the release build (not debuggable, R8, shrunk resources), installable next to
        // the user's app: another application id and name, plus the development hooks of
        // src/devtools (fixed clocks and data for comparable screenshots and measurements).
        create("perf") {
            initWith(getByName("release"))
            applicationIdSuffix = ".perf"
            matchingFallbacks += listOf("release")
        }
    }

    sourceSets {
        getByName("debug") {
            kotlin.directories.add("src/devtools/java")
            assets.directories.add("src/devtools/assets")
        }
        getByName("perf") {
            kotlin.directories.add("src/devtools/java")
            assets.directories.add("src/devtools/assets")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

/**
 * Puts the project's THIRD_PARTY_NOTICES.md into the app's assets (shown on the open source
 * licenses page), so the page always shows the file in the repository root.
 */
abstract class ThirdPartyNoticesTask : DefaultTask() {
    @get:InputFile
    abstract val source: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val out = outputDir.get().asFile
        out.mkdirs()
        source.get().asFile.copyTo(File(out, "THIRD_PARTY_NOTICES.md"), overwrite = true)
    }
}

val thirdPartyNotices = tasks.register<ThirdPartyNoticesTask>("thirdPartyNoticesAsset") {
    source.set(rootProject.layout.projectDirectory.file("THIRD_PARTY_NOTICES.md"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(thirdPartyNotices, ThirdPartyNoticesTask::outputDir)
    }
}

ksp {
    // Export Room schemas so future migrations can be verified.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    // Liquid glass: pinned, see gradle/libs.versions.toml for why these must not be upgraded.
    implementation(libs.kyant.backdrop)
    implementation(libs.kyant.shapes)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.work.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}