package com.example.appsandbox.identity

import android.content.AttributionSource
import android.os.Build
import android.util.Log

class SystemIdentityBridge(private val identity: RuntimeIdentity) {
    val instanceId: String get() = identity.instanceId
    val processSlot: Int get() = identity.processSlot

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

    fun logIdentityRewrite(service: String, method: String, rewrite: String) {
        Log.i(TAG, "VBINDER_IDENTITY api=${Build.VERSION.SDK_INT} service=$service method=$method " +
            "instance=${identity.instanceId} slot=${identity.processSlot} logical.package=${identity.guestPackageName} " +
            "logical.uid=${identity.virtualUid} physical.package=${identity.hostPackageName} physical.uid=${identity.hostUid} " +
            "rewrite=$rewrite")
    }

    fun log(service: String, method: String, before: Array<out Any?>?, after: Array<out Any?>?) {
        val original = before?.filterIsInstance<AttributionSource>()?.firstOrNull()
        val physical = after?.filterIsInstance<AttributionSource>()?.firstOrNull()
        Log.i(TAG, "IDENTITY_BRIDGE api=${Build.VERSION.SDK_INT} service=$service method=$method " +
            "instance=${identity.instanceId} slot=${identity.processSlot} logical.package=${identity.guestPackageName} " +
            "logical.uid=${identity.virtualUid} physical.package=${identity.hostPackageName} physical.uid=${identity.hostUid} " +
            "attribution.before=${original?.packageName}/${original?.uid} attribution.after=${physical?.packageName}/${physical?.uid} action=translate-system-boundary")
    }

    companion object { private const val TAG = "AppSandbox.M2.1" }
}
