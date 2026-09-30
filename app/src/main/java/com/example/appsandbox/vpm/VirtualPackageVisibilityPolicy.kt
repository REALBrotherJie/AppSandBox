package com.example.appsandbox.vpm

import com.example.appsandbox.identity.RuntimeIdentity

enum class PackageQueryRoute { VIRTUAL, SYSTEM, HOST, DENY }

class VirtualPackageVisibilityPolicy(private val identity: RuntimeIdentity) {
    fun route(packageName: String?): PackageQueryRoute = when {
        packageName == identity.guestPackageName -> PackageQueryRoute.VIRTUAL
        packageName == identity.hostPackageName -> PackageQueryRoute.HOST
        packageName == "android" || packageName?.startsWith("com.android.") == true -> PackageQueryRoute.SYSTEM
        packageName == null -> PackageQueryRoute.SYSTEM
        else -> PackageQueryRoute.DENY
    }
}
