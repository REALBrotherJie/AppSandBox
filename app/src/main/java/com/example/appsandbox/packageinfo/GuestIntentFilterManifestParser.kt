package com.example.appsandbox.packageinfo

import com.example.appsandbox.model.resolver.GuestComponentNames
import com.example.appsandbox.model.resolver.GuestComponentType
import com.example.appsandbox.model.resolver.GuestIntentFilter
import com.example.appsandbox.model.resolver.GuestIntentFilterException
import com.example.appsandbox.model.resolver.GuestIntentFilterNormalizer
import com.example.appsandbox.model.resolver.GuestRawIntentData

class GuestIntentFilterManifestException(
    val reason: GuestIntentFilterManifestReason,
    message: String = reason.code
) : IllegalArgumentException(message)

enum class GuestIntentFilterManifestReason(val code: String) {
    UNSUPPORTED_COMPONENT("unsupported-component"),
    FILTER_OUTSIDE_COMPONENT("filter-outside-component"),
    UNSUPPORTED_ATTRIBUTE("unsupported-filter-attribute"),
    UNSUPPORTED_CHILD("unsupported-filter-child"),
    INVALID_ATTRIBUTE("invalid-filter-attribute"),
    FILTER_REJECTED("unsupported-filter-pattern")
}

object GuestIntentFilterManifestParser {
    private val COMPONENTS = mapOf(
        "activity" to GuestComponentType.ACTIVITY,
        "service" to GuestComponentType.SERVICE,
        "receiver" to GuestComponentType.RECEIVER
    )

    fun parse(
        root: GuestManifestNode,
        packageName: String,
        revisionId: String
    ): List<GuestIntentFilter> {
        val filters = mutableListOf<GuestIntentFilter>()
        walk(root, packageName, revisionId, null, filters)
        return filters
            .distinctBy { it.canonicalKey }
            .sortedWith(compareBy({ it.componentType.code }, { it.componentClassName }, { it.canonicalKey }))
    }

    private fun walk(
        node: GuestManifestNode,
        packageName: String,
        revisionId: String,
        parentComponent: ComponentCursor?,
        filters: MutableList<GuestIntentFilter>
    ) {
        val component = COMPONENTS[node.name]?.let { type ->
            ComponentCursor(type, readComponentName(node, packageName))
        } ?: if (node.name == "provider") {
            ComponentCursor(GuestComponentType.PROVIDER, readComponentName(node, packageName))
        } else {
            parentComponent
        }
        if (node.name == "intent-filter") {
            val current = parentComponent
                ?: throw GuestIntentFilterManifestException(
                    GuestIntentFilterManifestReason.FILTER_OUTSIDE_COMPONENT
                )
            if (current.type == GuestComponentType.PROVIDER) {
                throw GuestIntentFilterManifestException(
                    GuestIntentFilterManifestReason.UNSUPPORTED_COMPONENT,
                    "Provider intent filters are unsupported"
                )
            }
            filters += parseFilter(node, packageName, revisionId, current)
            return
        }
        node.children.forEach { child ->
            walk(child, packageName, revisionId, component, filters)
        }
    }

    private fun parseFilter(
        node: GuestManifestNode,
        packageName: String,
        revisionId: String,
        component: ComponentCursor
    ): GuestIntentFilter {
        validateAttributes(node, setOf("priority", "autoVerify"))
        val priority = node.androidAttribute("priority")?.value?.let(::parsePriority) ?: 0
        val autoVerify = node.androidAttribute("autoVerify")?.value?.let(::parseBoolean) ?: false
        val actions = mutableListOf<String>()
        val categories = mutableListOf<String>()
        val data = mutableListOf<GuestRawIntentData>()
        node.children.forEach { child ->
            when (child.name) {
                "action" -> {
                    validateAttributes(child, setOf("name"))
                    actions += child.androidAttribute("name")?.value
                        ?: throw GuestIntentFilterManifestException(
                            GuestIntentFilterManifestReason.INVALID_ATTRIBUTE,
                            "action has no name"
                        )
                }

                "category" -> {
                    validateAttributes(child, setOf("name"))
                    categories += child.androidAttribute("name")?.value
                        ?: throw GuestIntentFilterManifestException(
                            GuestIntentFilterManifestReason.INVALID_ATTRIBUTE,
                            "category has no name"
                        )
                }

                "data" -> {
                    validateAttributes(child, setOf("mimeType", "scheme", "host", "path"))
                    data += GuestRawIntentData(
                        mimeType = child.androidAttribute("mimeType")?.value,
                        scheme = child.androidAttribute("scheme")?.value,
                        host = child.androidAttribute("host")?.value,
                        path = child.androidAttribute("path")?.value
                    )
                }

                else -> throw GuestIntentFilterManifestException(
                    GuestIntentFilterManifestReason.UNSUPPORTED_CHILD,
                    "unsupported intent-filter child=${child.name}"
                )
            }
        }
        return try {
            GuestIntentFilterNormalizer.normalize(
                revisionId = revisionId,
                packageName = packageName,
                componentClassName = component.className,
                componentType = component.type,
                actions = actions,
                categories = categories,
                dataDeclarations = data,
                priority = priority,
                autoVerify = autoVerify
            )
        } catch (error: GuestIntentFilterException) {
            throw GuestIntentFilterManifestException(
                GuestIntentFilterManifestReason.FILTER_REJECTED,
                error.reason.code
            )
        } catch (error: IllegalArgumentException) {
            throw GuestIntentFilterManifestException(
                GuestIntentFilterManifestReason.FILTER_REJECTED,
                error.message ?: "filter normalization failed"
            )
        }
    }

    private fun readComponentName(node: GuestManifestNode, packageName: String): String {
        val rawName = node.androidAttribute("name")?.value
            ?: throw GuestIntentFilterManifestException(
                GuestIntentFilterManifestReason.INVALID_ATTRIBUTE,
                "component has no name"
            )
        return try {
            GuestComponentNames.normalize(packageName, rawName)
        } catch (error: IllegalArgumentException) {
            throw GuestIntentFilterManifestException(
                GuestIntentFilterManifestReason.INVALID_ATTRIBUTE,
                error.message ?: "component name is invalid"
            )
        }
    }

    private fun validateAttributes(node: GuestManifestNode, allowed: Set<String>) {
        node.attributes.forEach { attribute ->
            if (attribute.namespace != GuestBinaryXmlManifest.ANDROID_NS ||
                attribute.name !in allowed
            ) {
                throw GuestIntentFilterManifestException(
                    GuestIntentFilterManifestReason.UNSUPPORTED_ATTRIBUTE,
                    "unsupported intent-filter attribute=${attribute.name}"
                )
            }
        }
    }

    private fun parsePriority(raw: String): Int =
        raw.toIntOrNull() ?: throw GuestIntentFilterManifestException(
            GuestIntentFilterManifestReason.INVALID_ATTRIBUTE,
            "priority is not an integer"
        )

    private fun parseBoolean(raw: String): Boolean =
        when (raw) {
            "true" -> true
            "false" -> false
            else -> throw GuestIntentFilterManifestException(
                GuestIntentFilterManifestReason.INVALID_ATTRIBUTE,
                "autoVerify is not boolean"
            )
        }

    private data class ComponentCursor(
        val type: GuestComponentType,
        val className: String
    )
}
