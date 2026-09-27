plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.appsandbox.intentfilterfixture"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.appsandbox.intentfilterfixture"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    flavorDimensions += "revision"
    productFlavors {
        create("intentV1") {
            dimension = "revision"
        }
        create("intentV2") {
            dimension = "revision"
        }
    }
}
