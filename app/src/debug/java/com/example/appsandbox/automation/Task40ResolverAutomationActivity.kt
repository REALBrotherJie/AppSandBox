package com.example.appsandbox.automation

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import com.example.appsandbox.model.resolver.GuestComponentType
import com.example.appsandbox.resolver.GuestCallerScope
import com.example.appsandbox.resolver.GuestComponentRequest
import com.example.appsandbox.resolver.GuestComponentResolver
import com.example.appsandbox.resolver.GuestResolutionResult
import com.example.appsandbox.storage.GuestStore

class Task40ResolverAutomationActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val packageName = intent.getStringExtra(EXTRA_PACKAGE)
        val revisionOrdinal = intent.getIntExtra(EXTRA_REVISION_ORDINAL, 0)
        val revision = packageName?.let {
            GuestStore(this).recordsForPackage(it).sortedBy { record -> record.importedAt }.getOrNull(revisionOrdinal)
        }
        val text = when {
            packageName == null -> "REJECTED reason=invalid-request"
            revision == null -> "REJECTED reason=revision-not-found"
            else -> resolve(revision.revisionId, packageName)
        }
        setContentView(TextView(this).apply {
            this.text = text
            textSize = 16f
            setPadding(32, 32, 32, 32)
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
        return when (val result = GuestComponentResolver(GuestStore(this)).resolve(
            GuestComponentRequest(
                revisionId = revisionId,
                packageName = packageName,
                className = intent.getStringExtra(EXTRA_CLASS).orEmpty(),
                type = type,
                callerScope = callerScope
            )
        )) {
            is GuestResolutionResult.Resolved ->
                "REVISION=$revisionId RESOLVED type=${result.component.type.code} class=${result.component.className}"
            is GuestResolutionResult.Rejected ->
                "REVISION=$revisionId REJECTED reason=${result.reason.code}"
        }
    }

    companion object {
        const val EXTRA_PACKAGE = "packageName"
        const val EXTRA_REVISION_ORDINAL = "revisionOrdinal"
        const val EXTRA_CLASS = "className"
        const val EXTRA_TYPE = "componentType"
        const val EXTRA_CALLER_SCOPE = "callerScope"
    }
}
