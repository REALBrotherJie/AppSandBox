pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AppSandbox"
include(":app")
include(":test-guests:GuestTestApp")
include(":test-guests:IndependentGuest")
include(":test-guests:ContractInvalidFixtures")
include(":test-guests:ResolverFixtures")
include(":test-guests:IntentFilterFixtures")
include(":test-guests:ZeroAdaptActivityApp")
include(":test-guests:RuntimeCorrectionFixture")
