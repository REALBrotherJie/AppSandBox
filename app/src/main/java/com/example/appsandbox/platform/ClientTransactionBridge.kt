package com.example.appsandbox.platform

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import java.lang.reflect.Field
import com.example.appsandbox.virtual.LaunchEnvelope

class ClientTransactionBridge : PlatformBridge {
    data class LaunchRecord(private val item: Any, private val intentField: Field, private val infoField: Field) {
        val intent: Intent get() = intentField.get(item) as Intent
        fun replace(intent: Intent, activityInfo: ActivityInfo) {
            intentField.set(item, intent)
            infoField.set(item, activityInfo)
        }
    }

    data class NewIntentRecord(private val item: Any, private val field: Field) {
        fun restoreGuestIntents(): Int {
            @Suppress("UNCHECKED_CAST")
            val intents = (field.get(item) as? List<Any>)?.toMutableList() ?: return 0
            var changed = 0
            intents.indices.forEach { index ->
                val current = intents[index] as? Intent ?: return@forEach
                val envelope = LaunchEnvelope.from(current) ?: return@forEach
                val referrer = runCatching {
                    current.javaClass.getDeclaredField("mReferrer").apply { isAccessible = true }.get(current) as? String
                }.getOrNull()
                val replacement = if (current.javaClass.name == "com.android.internal.content.ReferrerIntent") {
                    current.javaClass.getDeclaredConstructor(Intent::class.java, String::class.java)
                        .apply { isAccessible = true }.newInstance(envelope.originalIntent, referrer ?: envelope.packageName)
                } else Intent(envelope.originalIntent)
                intents[index] = replacement
                changed++
            }
            if (changed > 0) field.set(item, intents)
            return changed
        }
    }

    fun findLaunchRecords(transaction: Any): List<LaunchRecord> {
        val items = transactionItems(transaction)
        return items.mapNotNull { item ->
            if (!item.javaClass.name.endsWith("LaunchActivityItem")) return@mapNotNull null
            val intent = findField(item.javaClass, "mIntent") ?: return@mapNotNull null
            val info = findField(item.javaClass, "mInfo") ?: return@mapNotNull null
            intent.isAccessible = true
            info.isAccessible = true
            if (!Intent::class.java.isAssignableFrom(intent.type) || !ActivityInfo::class.java.isAssignableFrom(info.type)) null
            else LaunchRecord(item, intent, info)
        }
    }

    fun findNewIntentRecords(transaction: Any): List<NewIntentRecord> = transactionItems(transaction).mapNotNull { item ->
        if (!item.javaClass.name.contains("NewIntent")) return@mapNotNull null
        val fields = allFields(item.javaClass)
        val field = findField(item.javaClass, "mIntents")
            ?: fields.firstOrNull { Iterable::class.java.isAssignableFrom(it.type) }
            ?: return@mapNotNull null
        field.isAccessible = true
        NewIntentRecord(item, field)
    }

    override fun probe(): PlatformProbe = runCatching {
        val transaction = Class.forName("android.app.servertransaction.ClientTransaction")
        val fields = transactionFields(transaction).map { "${it.declaringClass.name}#${it.name}" }
        require(fields.isNotEmpty()) { "no ClientTransaction item-list field for API ${Build.VERSION.SDK_INT}" }
        PlatformProbe(supported = true, resolvedMembers = fields)
    }.getOrElse { PlatformProbe(supported = false, failureReason = it.rootCause().let { root -> "${root.javaClass.name}: ${root.message}" }) }

    private fun transactionFields(instance: Any): List<Field> = transactionFields(instance.javaClass)

    private fun transactionItems(transaction: Any): List<Any> = transactionFields(transaction).flatMap { field ->
        field.isAccessible = true
        when (val value = field.get(transaction)) {
            is Iterable<*> -> value.filterNotNull()
            else -> emptyList()
        }
    }

    private fun transactionFields(type: Class<*>): List<Field> = allFields(type).filter {
        it.name == "mActivityCallbacks" || it.name == "mTransactionItems" || Iterable::class.java.isAssignableFrom(it.type)
    }

    private fun allFields(type: Class<*>): List<Field> = generateSequence(type) { it.superclass }.flatMap { it.declaredFields.asSequence() }.toList()

    private fun findField(type: Class<*>, name: String): Field? =
        generateSequence(type) { it.superclass }.mapNotNull { runCatching { it.getDeclaredField(name) }.getOrNull() }.firstOrNull()
}
