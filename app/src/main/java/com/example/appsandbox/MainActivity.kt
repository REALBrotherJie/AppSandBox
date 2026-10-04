package com.example.appsandbox

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.util.Log
import android.net.Uri
import android.widget.LinearLayout
import android.widget.TextView
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.appsandbox.hidden.HiddenApiAccess
import com.example.appsandbox.packageinfo.GuestPackageReader
import com.example.appsandbox.runtime.GuestRuntimeProtocol
import com.example.appsandbox.runtime.GuestRuntimeService
import com.example.appsandbox.runtime.VirtualActivityLauncher
import com.example.appsandbox.runtime.M10ArchitectureProbeCoordinator
import com.example.appsandbox.location.VirtualLocationCoordinatorClient
import com.example.appsandbox.location.VirtualLocationMode
import com.example.appsandbox.location.VirtualLocationPoint
import com.example.appsandbox.model.GuestPackageRecord
import com.example.appsandbox.storage.GuestStore
import com.example.appsandbox.vpm.VirtualPackageRegistry
import com.example.appsandbox.vpm.VirtualInstanceRecord
import java.util.concurrent.Executors
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private val importExecutor = Executors.newSingleThreadExecutor()
    private lateinit var installedApps: List<GuestPackageRecord>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.statusText)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainScroll)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bars.bottom + 32)
            insets
        }
        installedApps = emptyList()
        renderInstalledApps(installedApps)
        showStatus(getString(R.string.loading_apps))
        loadInstalledApps()
        renderInstances()
        findViewById<View>(R.id.importCard).visibility = if (BuildConfig.DEBUG) View.VISIBLE else View.GONE
        findViewById<View>(R.id.settingsButton).setOnClickListener { showAbout() }
        findViewById<android.widget.EditText>(R.id.searchInput).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString().orEmpty().trim().lowercase()
                renderInstalledApps(installedApps.filter { query.isEmpty() || it.appLabel.lowercase().contains(query) || it.packageName.lowercase().contains(query) })
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        findViewById<View>(R.id.importApkButton).setOnClickListener {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/vnd.android.package-archive"
            }, REQUEST_IMPORT_APK)
        }
        if (intent.getBooleanExtra("m10ArchProbe", false)) {
            M10ArchitectureProbeCoordinator(this).run()
            return
        }
        if (intent.action == ACTION_DELETE_VIRTUAL_INSTANCE) {
            deleteVirtualInstance(
                requireNotNull(intent.getStringExtra(EXTRA_GUEST_PACKAGE)),
                requireNotNull(intent.getStringExtra(EXTRA_INSTANCE_ID))
            )
            return
        }
        if (intent.action == ACTION_CONFIGURE_VIRTUAL_LOCATION) {
            configureVirtualLocation(intent)
            return
        }
        if (intent.action == ACTION_QUERY_VIRTUAL_LOCATION) {
            val instanceId = requireNotNull(intent.getStringExtra(EXTRA_INSTANCE_ID))
            val profile = VirtualLocationCoordinatorClient.get(this, instanceId)
            status.text = "Virtual location: $instanceId / ${profile?.mode ?: "ABSENT"}"
            Log.i("AppSandbox.M11", "VLOCATION_QUERY instance=$instanceId profile=${profile?.mode ?: "ABSENT"} generation=${profile?.generation ?: -1}")
            return
        }
        if (intent.hasExtra(EXTRA_STUB_SLOT) && !intent.hasExtra(EXTRA_GUEST_PACKAGE)) {
            runFoundationProbe(intent.getIntExtra(EXTRA_STUB_SLOT, 0))
        }
        intent.getStringExtra(EXTRA_GUEST_PACKAGE)?.let { packageName ->
            runCatching {
                VirtualActivityLauncher(this).launch(
                    packageName,
                    intent.getStringExtra(EXTRA_INSTANCE_ID) ?: "m2-default",
                    intent.getIntExtra(EXTRA_STUB_SLOT, 0),
                    Bundle(intent.extras ?: Bundle()).apply {
                        remove(EXTRA_GUEST_PACKAGE)
                        remove(EXTRA_INSTANCE_ID)
                        remove(EXTRA_STUB_SLOT)
                    }
                )
            }.onFailure { error ->
                Log.e(TAG, "M2 launch failed package=$packageName", error)
                status.append("\nM2 launch failed: ${error.javaClass.simpleName}: ${error.message}")
            }
        }
    }

    override fun onDestroy() {
        importExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun loadInstalledApps() {
        importExecutor.execute {
            val result = runCatching { GuestPackageReader(this).queryLaunchableInstalledApps() }
            runOnUiThread {
                result.onSuccess {
                    installedApps = it
                    renderInstalledApps(it)
                    status.visibility = View.GONE
                }.onFailure {
                    Log.e(TAG, "installed app discovery failed", it)
                    showStatus(getString(R.string.apps_load_failed), true)
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_IMPORT_APK || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        beginImport(uri)
    }

    private fun beginImport(uri: Uri) {
        val button = findViewById<View>(R.id.importApkButton)
        button.isEnabled = false
        button.alpha = 0.65f
        showStatus(getString(R.string.importing_apk))
        importExecutor.execute {
            val result = runCatching {
                val stream = requireNotNull(contentResolver.openInputStream(uri)) { "Unable to read selected file" }
                GuestStore(this).importApk(stream) { stagedPath ->
                    GuestPackageReader(this).read(stagedPath, stagedPath)
                }
            }
            runOnUiThread {
                button.isEnabled = true
                button.alpha = 1f
                result.onSuccess { record ->
                    showStatus(getString(R.string.import_success))
                    renderImportedRecord(record)
                }.onFailure { error ->
                    Log.e(TAG, "APK import failed", error)
                    showStatus(getString(R.string.import_failed), true)
                }
            }
        }
    }

    private fun showStatus(message: String, error: Boolean = false) {
        status.visibility = View.VISIBLE
        status.text = message
        status.setTextColor(getColor(if (error) R.color.sandbox_error else R.color.sandbox_text_secondary))
    }

    private fun renderImportedRecord(record: GuestPackageRecord) {
        val list = findViewById<LinearLayout>(R.id.guestLibraryList)
        findViewById<View>(R.id.emptyState).visibility = View.GONE
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 12 }
            radius = 18f
            cardElevation = 1f
            setCardBackgroundColor(getColor(R.color.sandbox_surface))
            strokeWidth = 1
            strokeColor = getColor(R.color.sandbox_primary)
            contentDescription = "${record.appLabel}, imported APK"
        }
        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 18)
        }
        details.addView(TextView(this).apply {
            text = record.appLabel
            setTextColor(getColor(R.color.sandbox_text))
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        details.addView(TextView(this).apply {
            text = "${record.packageName} · ${record.versionName ?: getString(R.string.version_unknown)}"
            setTextColor(getColor(R.color.sandbox_text_secondary))
            textSize = 13f
            setPadding(0, 6, 0, 0)
        })
        details.addView(TextView(this).apply {
            text = getString(R.string.imported_summary, record.componentSummary.activityCount, record.componentSummary.serviceCount, record.componentSummary.receiverCount, record.componentSummary.providerCount)
            setTextColor(getColor(R.color.sandbox_text_secondary))
            textSize = 14f
            setPadding(0, 12, 0, 0)
        })
        card.addView(details)
        list.addView(card, 0)
    }

    private fun renderInstalledApps(apps: List<GuestPackageRecord>) {
        val list = findViewById<LinearLayout>(R.id.guestLibraryList)
        val empty = findViewById<View>(R.id.emptyState)
        list.removeAllViews()
        empty.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
        apps.take(12).forEach { app ->
            val card = MaterialCardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
                radius = dp(18).toFloat()
                cardElevation = 0f
                setCardBackgroundColor(getColor(R.color.sandbox_surface))
                strokeWidth = 1
                strokeColor = getColor(R.color.sandbox_outline)
                contentDescription = "${app.appLabel}, ${app.packageName}"
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(16), dp(16), dp(16))
            }
            val icon = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(52), dp(52))
                setImageDrawable(packageManager.getApplicationIcon(app.packageName))
                contentDescription = null
            }
            val details = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(16) }
            }
            details.addView(TextView(this).apply {
                text = app.appLabel
                setTextColor(getColor(R.color.sandbox_text))
                textSize = 16f
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            details.addView(TextView(this).apply {
                text = "${app.packageName} · ${app.versionName ?: getString(R.string.version_unknown)}"
                setTextColor(getColor(R.color.sandbox_text_secondary))
                textSize = 13f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            row.addView(icon)
            row.addView(details)
            row.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = getString(R.string.create_space)
                minHeight = dp(48)
                isAllCaps = false
                setOnClickListener { createAndLaunch(app) }
            })
            card.addView(row)
            list.addView(card)
        }
    }

    private fun renderInstances() {
        val list = findViewById<LinearLayout>(R.id.instanceList)
        val records = VirtualPackageRegistry(this).readAll().flatMap { pkg -> pkg.instances.values.map { pkg.packageName to it } }
        list.removeAllViews()
        findViewById<View>(R.id.instancesEmpty).visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
        records.sortedBy { it.second.instanceId }.forEach { (packageName, instance) ->
            val label = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString() }.getOrDefault(packageName)
            val card = MaterialCardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
                radius = dp(18).toFloat(); cardElevation = 0f; strokeWidth = dp(1)
                strokeColor = getColor(R.color.sandbox_outline); setCardBackgroundColor(getColor(R.color.sandbox_surface))
            }
            val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(12)) }
            body.addView(TextView(this).apply { text = label; textSize = 16f; setTextColor(getColor(R.color.sandbox_text)); setTypeface(typeface, android.graphics.Typeface.BOLD) })
            body.addView(TextView(this).apply {
                text = getString(R.string.space_label, instance.instanceId.take(8)) + " · p${instance.processSlot}"
                textSize = 13f; setTextColor(getColor(R.color.sandbox_text_secondary)); setPadding(0, dp(3), 0, dp(8))
            })
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            actions.addView(actionButton(R.string.open_space) { launchExisting(packageName, instance) })
            actions.addView(actionButton(R.string.location) { chooseLocation(instance.instanceId) })
            actions.addView(actionButton(R.string.remove) { confirmDelete(label, packageName, instance.instanceId) })
            body.addView(actions); card.addView(body); list.addView(card)
        }
    }

    private fun actionButton(label: Int, action: () -> Unit) = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
        layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f); text = getString(label); isAllCaps = false
        setOnClickListener { action() }
    }

    private fun createAndLaunch(app: GuestPackageRecord) {
        val used = VirtualPackageRegistry(this).readAll().flatMap { it.instances.values }.map { it.processSlot }.toSet()
        val slot = (0..8).firstOrNull { it !in used }
        if (slot == null) { showStatus(getString(R.string.space_limit), true); return }
        showStatus(getString(R.string.creating_space))
        runCatching { VirtualActivityLauncher(this).launch(app.packageName, UUID.randomUUID().toString(), slot) }
            .onSuccess { renderInstances(); showStatus(getString(R.string.launching_space)) }
            .onFailure { Log.e(TAG, "create space failed", it); showStatus(getString(R.string.space_failed), true) }
    }

    private fun launchExisting(packageName: String, instance: VirtualInstanceRecord) {
        showStatus(getString(R.string.launching_space))
        runCatching { VirtualActivityLauncher(this).launch(packageName, instance.instanceId, instance.processSlot) }
            .onFailure { Log.e(TAG, "launch space failed", it); showStatus(getString(R.string.space_failed), true) }
    }

    private fun confirmDelete(label: String, packageName: String, instanceId: String) {
        MaterialAlertDialogBuilder(this).setTitle(R.string.remove_title)
            .setMessage(getString(R.string.remove_message, "$label · ${instanceId.take(8)}"))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.remove) { _, _ -> deleteVirtualInstance(packageName, instanceId) }.show()
    }

    private fun chooseLocation(instanceId: String) {
        val options = arrayOf(getString(R.string.location_real), getString(R.string.location_fixed), getString(R.string.location_unavailable))
        MaterialAlertDialogBuilder(this).setTitle(R.string.location_title).setSingleChoiceItems(options, -1) { dialog, which ->
            val mode = when (which) { 0 -> VirtualLocationMode.REAL_PASSTHROUGH; 1 -> VirtualLocationMode.FIXED; else -> VirtualLocationMode.UNAVAILABLE }
            val points = if (mode == VirtualLocationMode.FIXED) listOf(VirtualLocationPoint(31.2304, 121.4737, 10f)) else emptyList()
            runCatching { VirtualLocationCoordinatorClient.set(this, instanceId, mode, points, setOf("gps", "network")) }
                .onSuccess { Toast.makeText(this, R.string.location_saved, Toast.LENGTH_SHORT).show() }
                .onFailure { showStatus(getString(R.string.space_failed), true) }
            dialog.dismiss()
        }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun showAbout() {
        MaterialAlertDialogBuilder(this).setTitle(R.string.about_title).setMessage(R.string.about_message).setPositiveButton(R.string.close, null).show()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun configureVirtualLocation(intent: Intent) {
        val instanceId = requireNotNull(intent.getStringExtra(EXTRA_INSTANCE_ID))
        val mode = VirtualLocationMode.valueOf(intent.getStringExtra("locationMode") ?: VirtualLocationMode.FIXED.name)
        val encoded = intent.getStringExtra("locationPoints").orEmpty()
        val points = encoded.split('~').filter(String::isNotBlank).map(VirtualLocationPoint::decode)
        val providers = intent.getStringExtra("locationProviders").orEmpty().split(',').filter(String::isNotBlank).toSet()
        val profile = VirtualLocationCoordinatorClient.set(this, instanceId, mode, points, providers)
        status.text = "Virtual location configured: $instanceId / ${profile.mode} / generation ${profile.generation}"
        Log.i("AppSandbox.M11", "VLOCATION_CONFIG instance=$instanceId mode=${profile.mode} generation=${profile.generation} points=${profile.points.size} providers=${profile.providers}")
    }

    private fun deleteVirtualInstance(packageName: String, instanceId: String) {
        lateinit var connection: ServiceConnection
        connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val replyTo = Messenger(android.os.Handler(mainLooper) { reply ->
                    val ok = reply.data.getBoolean(GuestRuntimeProtocol.KEY_OK)
                    status.text = if (ok) "Deleted $packageName / $instanceId" else
                        "Delete failed: ${reply.data.getString(GuestRuntimeProtocol.KEY_MESSAGE)}"
                    status.visibility = View.VISIBLE
                    if (ok) { status.text = getString(R.string.remove_success); renderInstances() }
                    Log.i(TAG, "delete-instance reply package=$packageName instance=$instanceId ok=$ok " +
                        "slot=${reply.data.getInt(GuestRuntimeProtocol.KEY_SLOT, -1)} " +
                        "running=${reply.data.getBoolean(GuestRuntimeProtocol.KEY_RUNNING)} " +
                        "storageRemoved=${reply.data.getBoolean(GuestRuntimeProtocol.KEY_STORAGE_REMOVED)}")
                    unbindService(connection)
                    true
                })
                Messenger(binder).send(Message.obtain(null, GuestRuntimeProtocol.MSG_DELETE_INSTANCE).apply {
                    data = Bundle().apply {
                        putString(GuestRuntimeProtocol.KEY_PACKAGE_NAME, packageName)
                        putString(GuestRuntimeProtocol.KEY_INSTANCE_ID, instanceId)
                    }
                    this.replyTo = replyTo
                })
            }
            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }
        bindService(Intent(this, GuestRuntimeService::class.java), connection, BIND_AUTO_CREATE)
    }

    private fun runFoundationProbe(slot: Int) {
        val hidden = HiddenApiAccess.probe()
        Log.i(TAG, "hidden-api $hidden")
        status.append("\nHidden API: ${if (hidden.isSuccess) "OK" else hidden.exceptionOrNull()?.message}")
        bindService(Intent(this, GuestRuntimeService::class.java), object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val request = Message.obtain(null, GuestRuntimeProtocol.MSG_ALLOCATE).apply {
                    data = Bundle().apply {
                        putString(GuestRuntimeProtocol.KEY_INSTANCE_ID, "m1-probe")
                        putInt(GuestRuntimeProtocol.KEY_PREFERRED_SLOT, slot)
                    }
                    replyTo = Messenger(android.os.Handler(mainLooper) { reply ->
                        val ok = reply.data.getBoolean(GuestRuntimeProtocol.KEY_OK)
                        val allocated = reply.data.getInt(GuestRuntimeProtocol.KEY_SLOT, -1)
                        status.append("\nStub pool: ${if (ok) "p$allocated started" else reply.data.getString(GuestRuntimeProtocol.KEY_MESSAGE)}")
                        Log.i(TAG, "stub-pool ok=$ok slot=$allocated")
                        true
                    })
                }
                Messenger(binder).send(request)
            }

            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }, BIND_AUTO_CREATE)
    }

    companion object {
        const val EXTRA_STUB_SLOT = "stubSlot"
        const val EXTRA_GUEST_PACKAGE = "guestPackage"
        const val EXTRA_INSTANCE_ID = "instanceId"
        const val ACTION_DELETE_VIRTUAL_INSTANCE = "com.example.appsandbox.action.DELETE_VIRTUAL_INSTANCE"
        const val ACTION_CONFIGURE_VIRTUAL_LOCATION = "com.example.appsandbox.action.CONFIGURE_VIRTUAL_LOCATION"
        const val ACTION_QUERY_VIRTUAL_LOCATION = "com.example.appsandbox.action.QUERY_VIRTUAL_LOCATION"
        private const val TAG = "AppSandbox.M1"
        private const val REQUEST_IMPORT_APK = 4101
    }
}
