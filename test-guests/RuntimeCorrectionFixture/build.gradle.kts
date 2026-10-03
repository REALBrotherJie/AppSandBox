plugins { id("com.android.application") }

// Fixture for Generic Runtime Correction 15: Guest-packaged android.*/javax.* classes,
// split-only native code (extractNativeLibs=false) and Guest INTERNET/NETWORK_STATE declarations.
android {
    namespace = "com.example.r15fixture"
    compileSdk = 36
    ndkVersion = "23.1.7779620"
    defaultConfig {
        applicationId = "com.example.r15fixture"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    flavorDimensions += "network"
    productFlavors {
        create("withNetwork") { dimension = "network"; applicationIdSuffix = ".withnetwork" }
        create("noNetwork") { dimension = "network"; applicationIdSuffix = ".nonetwork" }
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    packaging { jniLibs { useLegacyPackaging = false } }
    bundle { abi { enableSplit = true } }
}
