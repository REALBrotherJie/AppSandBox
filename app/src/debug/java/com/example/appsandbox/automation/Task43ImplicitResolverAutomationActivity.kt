package com.example.appsandbox.automation

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import java.io.File
import com.example.appsandbox.model.resolver.GuestComponentType
import com.example.appsandbox.resolver.GuestCallerScope
import com.example.appsandbox.resolver.GuestImplicitIntentRequest
import com.example.appsandbox.resolver.GuestImplicitIntentResolver
import com.example.appsandbox.resolver.GuestImplicitResolutionResult
import com.example.appsandbox.storage.GuestStore

class Task43ImplicitResolverAutomationActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val runId = intent.getStringExtra("runId")
        val report = runId?.let { File(filesDir, "task47-$it.result") }
        report?.writeText("status=STARTED\nrunId=$runId\napi=${android.os.Build.VERSION.SDK_INT}\nphase=resolver\nerrorCode=\n")
        val packageName = intent.getStringExtra(EXTRA_PACKAGE)
        val revisionOrdinal = intent.getIntExtra(EXTRA_REVISION_ORDINAL, 0)
        val revision = packageName?.let {
            GuestStore(this).recordsForPackage(it)
                .sortedBy { record -> record.importedAt }
                .getOrNull(revisionOrdinal)
        }
        val text = when {
            packageName == null -> "REJECTED reason=invalid-request"
            revision == null -> "REJECTED reason=revision-not-found"
            else -> resolve(revision.revisionId, packageName)
        }
        report?.writeText("status=PASS\nrunId=$runId\napi=${android.os.Build.VERSION.SDK_INT}\nphase=resolver\nerrorCode=\n$text\n")
        setContentView(TextView(this).apply {
            this.text = text
            textSize = 14f
            setPadding(24, 24, 24, 24)
        })
    }

    private fun resolve(revisionId: String, packageName: String): String {
        val type = runCatching {
            GuestComponentType.fromCode(intent.getStringExtra(EXTRA_TYPE).orEmpty())
        }.getOrElse { return "REVISION=$revisionId REJECTED reason=invalid-request" }
        val callerScope = runCatching {
            GuestCallerScope.valueOf(
                intent.getStringExtra(EXTRA_CALLER_SCOPE) ?: GuestCallerScope.HOST_EXTERNAL.name
            )
        }.getOrDefault(GuestCallerScope.HOST_EXTERNAL)
        val categories = (intent.getStringArrayListExtra(EXTRA_CATEGORIES)?.toList()
            ?: intent.getStringArrayExtra(EXTRA_CATEGORIES)?.toList()
            ?: emptyList()).flatMap { it.split(',') }.filter { it.isNotBlank() }
        return when (val result = GuestImplicitIntentResolver(GuestStore(this)).resolve(
            GuestImplicitIntentRequest(
                revisionId = revisionId,
                packageName = packageName,
                componentType = type,
                callerScope = callerScope,
                action = intent.getStringExtra(EXTRA_ACTION).orEmpty(),
                categories = categories,
                mimeType = intent.getStringExtra(EXTRA_MIME),
                uri = intent.getStringExtra(EXTRA_URI)
            )
        )) {
            is GuestImplicitResolutionResult.Resolved -> buildString {
                append("REVISION=").append(revisionId)
                    .append(" RESOLVED count=").append(result.candidates.size)
                result.candidates.forEach { candidate ->
                    append("\nCANDIDATE class=").append(candidate.component.className)
                        .append(" type=").append(candidate.component.type.code)
                        .append(" priority=").append(candidate.filter.priority)
                        .append(" specificity=").append(candidate.specificity)
                        .append(" autoVerify=").append(candidate.filter.autoVerify)
                }
            }

            is GuestImplicitResolutionResult.Ambiguous ->
                "REVISION=$revisionId AMBIGUOUS candidates=${result.candidates.size}"

            is GuestImplicitResolutionResult.Rejected ->
                "REVISION=$revisionId REJECTED reason=${result.reason.code}"
        }
    }

    companion object {
        const val EXTRA_PACKAGE = "packageName"
        const val EXTRA_REVISION_ORDINAL = "revisionOrdinal"
        const val EXTRA_TYPE = "componentType"
        const val EXTRA_CALLER_SCOPE = "callerScope"
        const val EXTRA_ACTION = "action"
        const val EXTRA_CATEGORIES = "categories"
        const val EXTRA_MIME = "mimeType"
        const val EXTRA_URI = "uri"
    }
}
