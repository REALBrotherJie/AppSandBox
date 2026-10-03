package com.example.appsandbox.runtime

import android.util.Log
import dalvik.system.DexClassLoader

enum class ClassLoadingDomain { SYSTEM, RUNTIME_BRIDGE, GUEST }

enum class SystemClassSource { PLATFORM, GUEST, HOST }

class GuestDomainClassLoader(
    private val guestDexPath: String,
    optimizedDirectory: String,
    librarySearchPath: String?,
    private val hostLoader: ClassLoader,
    private val platformLoader: ClassLoader? = android.content.Context::class.java.classLoader
) : DexClassLoader(guestDexPath, optimizedDirectory, librarySearchPath, hostLoader) {
    override fun loadClass(name: String, resolve: Boolean): Class<*> = synchronized(this) {
        findLoadedClass(name)?.let { return it }
        val domain = domainOf(name)
        val loaded = when (domain) {
            ClassLoadingDomain.SYSTEM -> loadSystemClass(name)
            ClassLoadingDomain.RUNTIME_BRIDGE -> hostLoader.loadClass(name)
            ClassLoadingDomain.GUEST -> runCatching { findClass(name) }.getOrElse { hostLoader.loadClass(name) }
        }
        if (resolve) resolveClass(loaded)
        if (com.example.appsandbox.BuildConfig.DEBUG && shouldDiagnose(name)) {
            val source = if (loaded.classLoader === this) guestDexPath
                else loaded.protectionDomain?.codeSource?.location?.toString() ?: "boot/system"
            Log.i(TAG, "CLASSLOAD class=$name domain=$domain loader=${loaded.classLoader} source=$source " +
                "decision=${if (domain == ClassLoadingDomain.GUEST && loaded.classLoader === this) "guest-first" else "parent"}")
        }
        loaded
    }

    private fun loadSystemClass(name: String): Class<*> =
        resolveInOrder(name, systemLookupOrder(name)) { source ->
            when (source) {
                SystemClassSource.PLATFORM -> (platformLoader ?: hostLoader).loadClass(name)
                SystemClassSource.GUEST -> findClass(name)
                SystemClassSource.HOST -> hostLoader.loadClass(name)
            }
        }

    companion object {
        private const val TAG = "AppSandbox.M3"
        private val systemPrefixes = arrayOf(
            "java.", "javax.", "android.", "dalvik.", "org.xml.", "org.w3c.", "org.json.", "sun."
        )
        private val runtimePrefixes = arrayOf(
            "com.example.appsandbox.runtime.",
            "com.example.appsandbox.platform.",
            "com.example.appsandbox.identity.",
            "com.example.appsandbox.virtual.",
            "com.example.appsandbox.vpm."
        )

        // Framework classes come from the boot class path. Guests may package their own
        // javax, android.support and android classes, and the host APK ships some of the same
        // names (androidx.core's android.support.v4 AIDL compat), so the guest dex must be
        // consulted before the host loader. java.* stays platform-only.
        fun systemLookupOrder(name: String): List<SystemClassSource> =
            if (name.startsWith("java.")) listOf(SystemClassSource.PLATFORM, SystemClassSource.HOST)
            else listOf(SystemClassSource.PLATFORM, SystemClassSource.GUEST, SystemClassSource.HOST)

        fun <T> resolveInOrder(name: String, order: List<SystemClassSource>, lookup: (SystemClassSource) -> T): T {
            var failure: Throwable? = null
            for (source in order) {
                try {
                    return lookup(source)
                } catch (e: ClassNotFoundException) {
                    failure = failure ?: e
                } catch (e: LinkageError) {
                    failure = failure ?: e
                }
            }
            throw ClassNotFoundException(name, failure)
        }

        fun domainOf(name: String): ClassLoadingDomain = when {
            systemPrefixes.any(name::startsWith) -> ClassLoadingDomain.SYSTEM
            runtimePrefixes.any(name::startsWith) -> ClassLoadingDomain.RUNTIME_BRIDGE
            else -> ClassLoadingDomain.GUEST
        }

        private fun shouldDiagnose(name: String) =
            name.startsWith("androidx.") || name.startsWith("kotlin.") || name.contains("zeroadapt") || name.contains("mahoshojo")
    }
}
