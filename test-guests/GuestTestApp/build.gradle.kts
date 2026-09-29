plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.appsandbox.testguest"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.appsandbox.testguest"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
    flavorDimensions += "behavior"
    productFlavors {
        create("normal") { dimension = "behavior" }
        create("throwing") { dimension = "behavior" }
        create("constructorCrash") { dimension = "behavior" }
        create("blocking") { dimension = "behavior" }
    }
}
