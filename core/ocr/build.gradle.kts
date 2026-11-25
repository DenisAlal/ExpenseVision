plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.denalal.expensevision.core.ocr"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":core:model"))

    implementation(libs.onnxruntime.android)
    implementation(libs.opencv)

    // Clipper (de.lighti.clipper 6.4.2) не опубликован в Maven Central, поэтому
    // подключается локальным jar. Используется только в unclip() на этапе детекции.
    // SHA-256: DF1020524CC2CAD78D57A6928CF94D00E8558397CCF8D227D2C8F4AF1023B970
    implementation(files("libs/Clipper-6.4.2.jar"))
}
