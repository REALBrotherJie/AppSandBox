plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.appsandbox.resolverfixture"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.appsandbox.resolverfixture"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    flavorDimensions += "revision"
    productFlavors {
        create("resolverV1") {
            dimension = "revision"
        }
        create("resolverV2") {
            dimension = "revision"
        }
    }
}
