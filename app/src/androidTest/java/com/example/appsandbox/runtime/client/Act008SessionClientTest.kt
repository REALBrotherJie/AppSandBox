package com.example.appsandbox.runtime.client

import android.app.Activity
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.Bundle
import android.os.Looper
import android.os.Message
import android.os.Messenger

// Self-contained runner: no optional android.test library or test dependencies required.
class Act008SessionClientTest : Instrumentation() {
    private val instrumentation: Instrumentation get() = this
    private lateinit var binding: RecordingContext
    private lateinit var client: Act008SessionClient
    private lateinit var runtime: Messenger
    private var runtimeRequests = 0
    private var completions = 0
    private var failures = 0

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val tests = listOf(
            ::testCloseBeforeConnectedUnbindsExactlyOnceAndFailsPendingOnce,
            ::testLateConnectionCannotReopenClosedClient,
            ::testQueuedRequestsShareOneRegistration,
            ::testDisconnectedRegistrationIsStillReleasedOnClose,
            ::testNullBindingReleasesRegistrationAndAllowsNewBind,
            ::testThrowingCallbackDoesNotPreventOtherPendingFailures
        )
        val failed = mutableListOf<String>()
        for (test in tests) {
            try {
                setUp()
                test()
            } catch (failure: Throwable) {
                failed += "${test.name}: $failure"
            } finally {
                tearDown()
            }
        }
        finish(if (failed.isEmpty()) Activity.RESULT_OK else Activity.RESULT_CANCELED, Bundle().apply {
            putInt("tests", tests.size)
            putInt("failures", failed.size)
            putString("stream", if (failed.isEmpty()) "PASS ${tests.size} client lifecycle tests" else failed.joinToString("\n"))
        })
    }

    private fun setUp() {
        runtimeRequests = 0
        completions = 0
        failures = 0
        instrumentation.runOnMainSync {
            binding = RecordingContext(instrumentation.targetContext)
            client = Act008SessionClient(binding)
            runtime = Messenger(object : Handler(Looper.getMainLooper()) {
                override fun handleMessage(message: Message) {
                    runtimeRequests++
                }
            })
        }
    }

    private fun tearDown() {
        instrumentation.runOnMainSync { client.close() }
        instrumentation.waitForIdleSync()
    }

    fun testCloseBeforeConnectedUnbindsExactlyOnceAndFailsPendingOnce() {
        request()
        assertEquals(1, binding.binds)
        instrumentation.runOnMainSync {
            client.close()
            client.close()
            binding.connection.onServiceConnected(binding.component, runtime.binder)
        }
        instrumentation.waitForIdleSync()
        assertEquals(1, binding.unbinds)
        assertEquals(1, completions)
        assertEquals(1, failures)
        assertEquals(0, runtimeRequests)
    }

    fun testLateConnectionCannotReopenClosedClient() {
        request()
        instrumentation.runOnMainSync {
            binding.connection.onServiceConnected(binding.component, runtime.binder)
        }
        instrumentation.waitForIdleSync()
        assertEquals(1, runtimeRequests)
        instrumentation.runOnMainSync {
            client.close()
            binding.connection.onServiceConnected(binding.component, runtime.binder)
        }
        request()
        assertEquals(1, binding.binds)
        assertEquals(1, binding.unbinds)
        assertEquals(1, runtimeRequests)
        assertEquals(2, completions)
        assertEquals(2, failures)
    }

    fun testQueuedRequestsShareOneRegistration() {
        request()
        request()
        assertEquals(1, binding.binds)
        instrumentation.runOnMainSync { client.close() }
        assertEquals(1, binding.unbinds)
        assertEquals(2, completions)
        assertEquals(2, failures)
    }

    fun testDisconnectedRegistrationIsStillReleasedOnClose() {
        request()
        instrumentation.runOnMainSync {
            binding.connection.onServiceConnected(binding.component, runtime.binder)
            binding.connection.onServiceDisconnected(binding.component)
        }
        request()
        assertEquals(1, binding.binds)
        instrumentation.runOnMainSync { client.close() }
        assertEquals(1, binding.unbinds)
        assertEquals(2, completions)
        assertEquals(2, failures)
    }

    fun testNullBindingReleasesRegistrationAndAllowsNewBind() {
        request()
        instrumentation.runOnMainSync {
            binding.connection.onNullBinding(binding.component)
        }
        assertEquals(1, binding.unbinds)
        assertEquals(1, failures)
        request()
        assertEquals(2, binding.binds)
        instrumentation.runOnMainSync { client.close() }
        assertEquals(2, binding.unbinds)
        assertEquals(2, failures)
    }

    fun testThrowingCallbackDoesNotPreventOtherPendingFailures() {
        instrumentation.runOnMainSync {
            client.terminateRuntime {
                completions++
                throw IllegalStateException("callback failure")
            }
        }
        request()
        instrumentation.runOnMainSync {
            client.close()
            client.close()
        }
        assertEquals(2, completions)
        assertEquals(1, failures)
        assertEquals(1, binding.unbinds)
    }

    private fun request() {
        instrumentation.runOnMainSync {
            client.terminateRuntime {
                completions++
                if (it.isFailure) failures++
            }
        }
        instrumentation.waitForIdleSync()
    }

    private fun assertEquals(expected: Int, actual: Int) {
        check(expected == actual) { "expected $expected but was $actual" }
    }

    private class RecordingContext(base: Context) : ContextWrapper(base) {
        val component = ComponentName(base.packageName, "RecordingRuntime")
        lateinit var connection: ServiceConnection
        var binds = 0
        var unbinds = 0

        override fun bindService(intent: Intent, conn: ServiceConnection, flags: Int): Boolean {
            binds++
            connection = conn
            return true
        }

        override fun unbindService(conn: ServiceConnection) {
            check(conn === connection)
            unbinds++
        }
    }
}
