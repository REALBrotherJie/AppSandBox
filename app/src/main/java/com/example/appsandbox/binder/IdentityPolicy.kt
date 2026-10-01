package com.example.appsandbox.binder

import android.content.AttributionSource
import com.example.appsandbox.identity.RuntimeIdentity

class IdentityPolicy(private val identity: RuntimeIdentity) {
    fun physicalPackage(value: Any?): Any? = if (value == identity.guestPackageName) identity.hostPackageName else value
    fun physicalUid(value: Any?): Any? = if (value == identity.virtualUidNumber) identity.hostUid else value

    fun physicalAttribution(source: AttributionSource?): AttributionSource? {
        if (source == null) return null
        if (source.packageName == identity.hostPackageName && source.uid == identity.hostUid) return source
        val builder = AttributionSource.Builder(identity.hostUid)
            .setPackageName(identity.hostPackageName)
            .setAttributionTag(source.attributionTag)
        source.next?.let { builder.setNext(physicalAttribution(it)) }
        return builder.build()
    }

    fun rewriteAttributionArgs(args: Array<Any?>): Array<Any?> = args.map { value ->
        when (value) {
            is AttributionSource -> physicalAttribution(value)
            else -> value
        }
    }.toTypedArray()

    fun rewritePackageUidAt(args: Array<Any?>, packageIndexes: Set<Int>, uidIndexes: Set<Int>): Array<Any?> =
        args.copyOf().also { rewritten ->
            packageIndexes.forEach { if (it in rewritten.indices) rewritten[it] = physicalPackage(rewritten[it]) }
            uidIndexes.forEach { if (it in rewritten.indices) rewritten[it] = physicalUid(rewritten[it]) }
            rewritten.indices.forEach { index ->
                val value = rewritten[index]
                if (value is AttributionSource) rewritten[index] = physicalAttribution(value)
            }
        }
}
