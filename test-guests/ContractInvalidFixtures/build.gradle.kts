plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.appsandbox.contractinvalid"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.appsandbox.contractinvalid"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    flavorDimensions += "fixture"
    productFlavors {
        create("unknownVersion") {
            dimension = "fixture"
            applicationIdSuffix = ".unknownversion"
        }
        create("missingLayout") {
            dimension = "fixture"
            applicationIdSuffix = ".missinglayout"
        }
        create("missingActionRaw") {
            dimension = "fixture"
            applicationIdSuffix = ".missingactionraw"
        }
        create("unknownField") {
            dimension = "fixture"
            applicationIdSuffix = ".unknownfield"
        }
        create("unknownAction") {
            dimension = "fixture"
            applicationIdSuffix = ".unknownaction"
        }
        create("duplicateBinding") {
            dimension = "fixture"
            applicationIdSuffix = ".duplicatebinding"
        }
        create("invalidId") {
            dimension = "fixture"
            applicationIdSuffix = ".invalidid"
        }
        create("invalidStateKey") {
            dimension = "fixture"
            applicationIdSuffix = ".invalidstatekey"
        }
        create("wrongButtonType") {
            dimension = "fixture"
            applicationIdSuffix = ".wrongbuttontype"
        }
        create("wrongTextViewType") {
            dimension = "fixture"
            applicationIdSuffix = ".wrongtextviewtype"
        }
    }
}
