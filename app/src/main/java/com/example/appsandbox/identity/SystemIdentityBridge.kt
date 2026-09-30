package com.example.appsandbox.identity

import android.content.AttributionSource
import android.os.Build
import android.util.Log
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

class SystemIdentityBridge(private val identity: RuntimeIdentity) {
    fun logicalPackageName() = identity.guestPackageName
    fun physicalPackageName() = identity.hostPackageName
    fun logicalUid() = identity.virtualUid
    fun physicalUid() = identity.hostUid

    fun createPhysicalAttributionSource(source: AttributionSource?): AttributionSource? {
        if (source == null) return null
        val builder = AttributionSource.Builder(identity.hostUid)
            .setPackageName(identity.hostPackageName)
            .setAttributionTag(source.attributionTag)
        source.next?.let { builder.setNext(createPhysicalAttributionSource(it)) }
        return builder.build()
    }

    fun rewriteOutgoingSystemIdentity(value: Any?): Any? = when (value) {
        is AttributionSource -> createPhysicalAttributionSource(value)
        is Array<*> -> value.map(::rewriteOutgoingSystemIdentity).toTypedArray()
        else -> value
    }

    fun wrapContentProvider(provider: Any): Any {
        if (Proxy.isProxyClass(provider.javaClass)) return provider
        val providerInterface = Class.forName("android.content.IContentProvider")
        return Proxy.newProxyInstance(providerInterface.classLoader, arrayOf(providerInterface)) { _, method, arguments ->
            val rewritten = arguments?.map(::rewriteOutgoingSystemIdentity)?.toTypedArray()
            log("IContentProvider", method.name, arguments, rewritten)
            try {
                method.invoke(provider, *(rewritten ?: emptyArray()))
            } catch (error: InvocationTargetException) {
                logFailure("IContentProvider", method.name, error.targetException)
                throw error.targetException
            } catch (error: Throwable) {
                logFailure("IContentProvider", method.name, error)
                throw error
            }
        }
    }

    fun log(service: String, method: String, before: Array<out Any?>?, after: Array<out Any?>?) {
        val original = before?.filterIsInstance<AttributionSource>()?.firstOrNull()
        val physical = after?.filterIsInstance<AttributionSource>()?.firstOrNull()
        Log.i(TAG, "IDENTITY_BRIDGE api=${Build.VERSION.SDK_INT} service=$service method=$method " +
            "instance=${identity.instanceId} slot=${identity.processSlot} logical.package=${identity.guestPackageName} " +
            "logical.uid=${identity.virtualUid} physical.package=${identity.hostPackageName} physical.uid=${identity.hostUid} " +
            "attribution.before=${original?.packageName}/${original?.uid} attribution.after=${physical?.packageName}/${physical?.uid} action=translate-system-boundary")
    }

    private fun logFailure(service: String, method: String, error: Throwable) {
        Log.e(TAG, "IDENTITY_BRIDGE api=${Build.VERSION.SDK_INT} service=$service method=$method " +
            "instance=${identity.instanceId} slot=${identity.processSlot} logical.package=${identity.guestPackageName} " +
            "logical.uid=${identity.virtualUid} physical.package=${identity.hostPackageName} physical.uid=${identity.hostUid} " +
            "action=translation-failed exception=${error.javaClass.name}: ${error.message}", error)
    }

    companion object { private const val TAG = "AppSandbox.M2.1" }
}
