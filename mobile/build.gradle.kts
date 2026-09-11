plugins {
    id("com.android.application")
    kotlin("android")
    alias(libs.plugins.compose)
}

setupApp()

android {
    namespace = "com.adskipper"

    defaultConfig {
        applicationId = "com.tangzixiang.adskipper"

        // Ship arm64-v8a only. The project's own native code is already built
        // for arm64 alone (core/build.gradle.kts), but that abiFilters sits
        // inside externalNativeBuild and therefore does NOT filter the .so
        // files that dependencies package — MLKit's OCR ships x86, x86_64,
        // armeabi-v7a and arm64-v8a, so ~28 MB of libraries for ABIs this app
        // cannot run on were being included. Filtering here covers packaged
        // jniLibs too, and costs nothing: the app never ran on those ABIs.
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildFeatures.compose = true

    // GGUF/TFLite assets must stay uncompressed so AssetManager.openFd works
    // (noCompress in a library module does not propagate to packaging).
    aaptOptions {
        noCompress += "gguf"
        noCompress += "tflite"
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.coil.compose)
}
