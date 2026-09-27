package com.example.appsandbox.automation

import android.app.Activity
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import com.example.appsandbox.model.ComponentSummary
import com.example.appsandbox.model.GuestPackageRecord
import com.example.appsandbox.model.resolver.GuestComponent
import com.example.appsandbox.model.resolver.GuestComponentType
import com.example.appsandbox.model.resolver.GuestIntentFilterNormalizer
import com.example.appsandbox.model.resolver.GuestRawIntentData
import com.example.appsandbox.resolver.GuestCallerScope
import com.example.appsandbox.resolver.GuestImplicitIntentRequest
import com.example.appsandbox.resolver.GuestImplicitIntentResolver
import com.example.appsandbox.resolver.GuestImplicitResolutionPolicy
import com.example.appsandbox.resolver.GuestImplicitResolutionResult
import com.example.appsandbox.resolver.GuestRevisionSource
import java.io.File

class Task48IntentFilterParityActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val runId = intent.getStringExtra("runId") ?: return finish()
        val report = File(filesDir, "task48-parity-$runId.result")
        report.writeText("status=STARTED\nrunId=$runId\napi=${android.os.Build.VERSION.SDK_INT}\nphase=parity\nerrorCode=\n")
        val results = cases().map(::runCase)
        val mismatches = results.count { !it.same }
        report.writeText(buildString {
            append("status=").append(if (mismatches == 0) "PASS" else "FAIL").append('\n')
            append("runId=").append(runId).append('\n')
            append("api=").append(android.os.Build.VERSION.SDK_INT).append('\n')
            append("phase=parity\nerrorCode=")
            if (mismatches != 0) append("PARITY_MISMATCH")
            append("\ncases=").append(results.size).append("\nmismatches=").append(mismatches).append('\n')
            results.forEach { append(it.line).append('\n') }
        })
        finish()
    }

    private fun runCase(case: Case): Result {
        val frameworkFilter = IntentFilter().apply {
            addAction(case.filterAction)
            case.filterCategories.forEach(::addCategory)
            case.filterMime?.let(::addDataType)
            case.filterScheme?.let(::addDataScheme)
            case.filterHost?.let { addDataAuthority(it, null) }
            case.filterPath?.let { addDataPath(it, android.os.PatternMatcher.PATTERN_LITERAL) }
        }
        val frameworkCode = frameworkFilter.match(
            case.action, case.mime, case.uri?.scheme, case.uri,
            case.categories.takeIf { it.isNotEmpty() }?.toSet(), "Task48Parity"
        )
        val framework = frameworkCode >= 0 &&
            (!case.defaultOnly || frameworkFilter.hasCategory(Intent.CATEGORY_DEFAULT))
        val filter = GuestIntentFilterNormalizer.normalize(
            REVISION, PACKAGE, "$PACKAGE.Component", case.type,
            listOf(case.filterAction), case.filterCategories,
            if (case.filterMime == null && case.filterScheme == null) emptyList() else listOf(
                GuestRawIntentData(case.filterMime, case.filterScheme, case.filterHost, case.filterPath)
            ), 0, false
        )
        val component = GuestComponent(
            REVISION, PACKAGE, "$PACKAGE.Component", case.type, true, true,
            intentFilters = listOf(filter)
        )
        val record = GuestPackageRecord(
            REVISION, PACKAGE, "1", 1, filesDir.path, "Parity", 1,
            ComponentSummary.fromComponents(listOf(component)), components = listOf(component)
        )
        val appSandbox = GuestImplicitIntentResolver(GuestRevisionSource { record }).resolve(
            GuestImplicitIntentRequest(
                REVISION, PACKAGE, case.type, GuestCallerScope.HOST_EXTERNAL,
                case.action, case.categories, case.mime, case.uri?.toString(),
                if (case.defaultOnly) GuestImplicitResolutionPolicy.DEFAULT_ONLY else GuestImplicitResolutionPolicy.GENERAL
            )
        ) is GuestImplicitResolutionResult.Resolved
        return Result(framework == appSandbox,
            "caseId=${case.id} frameworkCode=$frameworkCode framework=$framework appSandbox=$appSandbox same=${framework == appSandbox}")
    }

    private fun cases() = listOf(
        Case("action-match"), Case("action-mismatch", action = "test.OTHER"),
        Case("category-match", filterCategories = listOf(DEFAULT, "test.CAT"), categories = listOf("test.CAT")),
        Case("category-mismatch", filterCategories = listOf(DEFAULT), categories = listOf("test.CAT")),
        Case("activity-default", filterCategories = listOf(DEFAULT), defaultOnly = true),
        Case("activity-default-missing", defaultOnly = true),
        Case("receiver-general", type = GuestComponentType.RECEIVER),
        Case("mime-exact", filterMime = "image/png", mime = "image/png"),
        Case("mime-wildcard", filterMime = "image/*", mime = "image/png"),
        Case("mime-mismatch", filterMime = "image/png", mime = "text/plain"),
        Case("scheme", filterScheme = "https", uri = Uri.parse("https://example.com/x")),
        Case("host", filterScheme = "https", filterHost = "example.com", uri = Uri.parse("https://example.com/x")),
        Case("path", filterScheme = "https", filterHost = "example.com", filterPath = "/x", uri = Uri.parse("https://example.com/x")),
        Case("mime-uri", filterMime = "image/png", filterScheme = "https", filterHost = "example.com", uri = Uri.parse("https://example.com/x"), mime = "image/png"),
        Case("scheme-case-negative", filterScheme = "HTTPS", uri = Uri.parse("https://example.com/x"))
    )

    data class Case(
        val id: String, val filterAction: String = "test.ACTION", val action: String = "test.ACTION",
        val filterCategories: List<String> = emptyList(), val categories: List<String> = emptyList(),
        val filterMime: String? = null, val mime: String? = filterMime,
        val filterScheme: String? = null, val filterHost: String? = null, val filterPath: String? = null,
        val uri: Uri? = null, val type: GuestComponentType = GuestComponentType.ACTIVITY,
        val defaultOnly: Boolean = false
    )
    data class Result(val same: Boolean, val line: String)

    companion object {
        private const val REVISION = "parity-revision"
        private const val PACKAGE = "com.example.parity"
        private const val DEFAULT = Intent.CATEGORY_DEFAULT
    }
}
