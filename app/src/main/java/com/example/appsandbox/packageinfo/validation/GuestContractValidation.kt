package com.example.appsandbox.packageinfo.validation

import android.content.res.Resources
import android.content.res.XmlResourceParser
import com.example.appsandbox.contract.GuestAction
import com.example.appsandbox.contract.GuestActionBinding
import com.example.appsandbox.contract.GuestActionSpecParser
import org.xmlpull.v1.XmlPullParser

enum class GuestContractRejection(val code: String) {
    UNKNOWN_VERSION("unknown-version"),
    MISSING_LAYOUT("missing-layout"),
    MISSING_LAYOUT_RESOURCE("missing-layout-resource"),
    MISSING_ACTION_SPEC("missing-action-spec"),
    MISSING_ACTION_RESOURCE("missing-action-resource"),
    UNKNOWN_FIELD("unknown-field"),
    UNKNOWN_ACTION("unknown-action"),
    DUPLICATE_BINDING("duplicate-binding"),
    DUPLICATE_ACTION("duplicate-action"),
    INVALID_ID("invalid-id"),
    INVALID_STATE_KEY("invalid-state-key"),
    INVALID_ACTION_SPEC("invalid-action-spec"),
    MISSING_CONTROL("missing-control"),
    WRONG_BUTTON_TYPE("wrong-button-type"),
    WRONG_TEXTVIEW_TYPE("wrong-textview-type"),
    INVALID_LAYOUT("invalid-layout")
}

class GuestContractValidationException(
    val rejection: GuestContractRejection
) : IllegalArgumentException("Unsupported Guest: ${rejection.code}")

data class GuestLayoutControl(val resourceName: String, val tagName: String)

object GuestContractValidation {
    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    private const val MAX_SPEC_LENGTH = 4096
    private val idPattern = Regex("[a-z][a-z0-9_]{0,63}")

    fun requireSupportedVersion(version: Int) {
        if (version != 1 && version != 2) reject(GuestContractRejection.UNKNOWN_VERSION)
    }

    fun requireMetadata(value: String?, rejection: GuestContractRejection): String {
        if (value.isNullOrBlank()) reject(rejection)
        return value
    }

    fun requireResource(resourceId: Int, rejection: GuestContractRejection) {
        if (resourceId == 0) reject(rejection)
    }

    fun parseActionSpec(text: String): Pair<String, List<GuestActionBinding>> {
        if (text.isEmpty() || text.length > MAX_SPEC_LENGTH) reject(GuestContractRejection.INVALID_ACTION_SPEC)
        var schema: String? = null
        var stateView: String? = null
        val viewIds = mutableSetOf<String>()
        val actions = mutableSetOf<GuestAction>()

        text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            val separator = line.indexOf('=')
            if (separator <= 0 || separator != line.lastIndexOf('=')) {
                reject(GuestContractRejection.INVALID_ACTION_SPEC)
            }
            when (line.substring(0, separator)) {
                "schema" -> {
                    if (schema != null) reject(GuestContractRejection.INVALID_ACTION_SPEC)
                    schema = line.substring(separator + 1)
                }

                "state" -> {
                    if (stateView != null) reject(GuestContractRejection.DUPLICATE_BINDING)
                    val parts = line.substring(separator + 1).split('|')
                    if (parts.size != 2 || !idPattern.matches(parts[0])) {
                        reject(GuestContractRejection.INVALID_ID)
                    }
                    if (parts[1] != "counter") reject(GuestContractRejection.INVALID_STATE_KEY)
                    stateView = parts[0]
                }

                "action" -> {
                    val parts = line.substring(separator + 1).split('|')
                    if (parts.size != 3 || !idPattern.matches(parts[0])) {
                        reject(GuestContractRejection.INVALID_ID)
                    }
                    val action = runCatching { GuestAction.fromWireName(parts[1]) }
                        .getOrElse { reject(GuestContractRejection.UNKNOWN_ACTION) }
                    if (parts[2] != "counter") reject(GuestContractRejection.INVALID_STATE_KEY)
                    if (!viewIds.add(parts[0])) reject(GuestContractRejection.DUPLICATE_BINDING)
                    if (!actions.add(action)) reject(GuestContractRejection.DUPLICATE_ACTION)
                }

                else -> reject(GuestContractRejection.UNKNOWN_FIELD)
            }
        }

        if (schema != "1" || stateView == null || viewIds.isEmpty()) {
            reject(GuestContractRejection.INVALID_ACTION_SPEC)
        }
        return runCatching { GuestActionSpecParser.parse(text) }
            .getOrElse { reject(GuestContractRejection.INVALID_ACTION_SPEC) }
    }

    fun validateLayout(
        resources: Resources,
        layoutId: Int,
        stateViewIdName: String,
        actions: List<GuestActionBinding>
    ) {
        val controls = readLayoutControls(resources, layoutId)
        val state = controls.firstOrNull { it.resourceName == stateViewIdName }
            ?: reject(GuestContractRejection.MISSING_CONTROL)
        if (!isTextView(state.tagName)) reject(GuestContractRejection.WRONG_TEXTVIEW_TYPE)

        actions.forEach { action ->
            val control = controls.firstOrNull { it.resourceName == action.viewIdName }
                ?: reject(GuestContractRejection.MISSING_CONTROL)
            if (!isButton(control.tagName)) reject(GuestContractRejection.WRONG_BUTTON_TYPE)
        }
    }

    private fun readLayoutControls(resources: Resources, layoutId: Int): List<GuestLayoutControl> {
        val parser: XmlResourceParser = try {
            resources.getXml(layoutId)
        } catch (_: Resources.NotFoundException) {
            reject(GuestContractRejection.INVALID_LAYOUT)
        }
        val controls = mutableListOf<GuestLayoutControl>()
        try {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                val resourceId = parser.getAttributeResourceValue(ANDROID_NS, "id", 0)
                if (resourceId == 0) continue
                val resourceName = runCatching { resources.getResourceEntryName(resourceId) }
                    .getOrElse { reject(GuestContractRejection.INVALID_LAYOUT) }
                controls += GuestLayoutControl(resourceName, parser.name)
            }
        } catch (_: Exception) {
            reject(GuestContractRejection.INVALID_LAYOUT)
        } finally {
            parser.close()
        }
        return controls
    }

    private fun isButton(tagName: String): Boolean =
        tagName == "Button" || tagName.endsWith(".Button") || tagName.endsWith("AppCompatButton")

    private fun isTextView(tagName: String): Boolean =
        tagName == "TextView" || tagName.endsWith(".TextView") || tagName.endsWith("AppCompatTextView")

    private fun reject(rejection: GuestContractRejection): Nothing =
        throw GuestContractValidationException(rejection)
}
