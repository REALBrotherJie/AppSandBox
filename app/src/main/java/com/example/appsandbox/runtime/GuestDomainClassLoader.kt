package com.example.appsandbox.runtime

import android.util.Log
import dalvik.system.DexClassLoader

enum class ClassLoadingDomain { SYSTEM, RUNTIME_BRIDGE, GUEST }

enum class ClassSource { PLATFORM, SHARED_LIBRARY, GUEST, HOST }

class GuestDomainClassLoader(
    private val guestDexPath: String,
    optimizedDirectory: String,
    librarySearchPath: String?,
    private val hostLoader: ClassLoader,
    private val platformLoader: ClassLoader? = android.content.Context::class.java.classLoader,
    /** Loader over the Guest's declared uses-library files (ApplicationInfo.sharedLibraryFiles). */
    private val sharedLibraryLoader: ClassLoader? = null
) : DexClassLoader(guestDexPath, optimizedDirectory, librarySearchPath, hostLoader) {
    override fun loadClass(name: String, resolve: Boolean): Class<*> = synchronized(this) {
        findLoadedClass(name)?.let { return it }
        val domain = domainOf(name)
        val loaded = when (domain) {
            ClassLoadingDomain.RUNTIME_BRIDGE -> hostLoader.loadClass(name)
            else -> resolveInOrder(name, lookupOrder(name, domain, sharedLibraryLoader != null), ::loadFrom)
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

    private fun loadFrom(source: ClassSource, name: String): Class<*> = when (source) {
        ClassSource.PLATFORM -> (platformLoader ?: hostLoader).loadClass(name)
        ClassSource.SHARED_LIBRARY -> requireNotNull(sharedLibraryLoader).loadClass(name)
        ClassSource.GUEST -> findClass(name)
        ClassSource.HOST -> hostLoader.loadClass(name)
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

        // Mirrors ART's app loader: boot class path, then uses-library loaders, then the APK's
        // own dex. Framework classes therefore come from the boot class path. Guests may package
        // their own javax, android.support and android classes, and the host APK ships some of the
        // same names (androidx.core's android.support.v4 AIDL compat), so the guest dex must be
        // consulted before the host loader. java.* stays platform-only. The host loader is only a
        // last resort for names nothing on the Guest's side defines.
        fun lookupOrder(name: String, domain: ClassLoadingDomain, hasSharedLibraries: Boolean): List<ClassSource> {
            if (domain == ClassLoadingDomain.SYSTEM && name.startsWith("java.")) return listOf(ClassSource.PLATFORM, ClassSource.HOST)
            return listOfNotNull(
                ClassSource.PLATFORM.takeIf { domain == ClassLoadingDomain.SYSTEM },
                ClassSource.SHARED_LIBRARY.takeIf { hasSharedLibraries },
                ClassSource.GUEST,
                ClassSource.HOST
            )
        }

        fun <T> resolveInOrder(name: String, order: List<ClassSource>, lookup: (ClassSource, String) -> T): T {
            var failure: Throwable? = null
            for (source in order) {
                try {
                    return lookup(source, name)
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
