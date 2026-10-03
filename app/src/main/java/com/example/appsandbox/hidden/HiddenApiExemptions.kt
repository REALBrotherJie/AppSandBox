package com.example.appsandbox.hidden

/** Installs hidden-API exemptions from a native thread, which ART treats as a trusted caller. */
internal object HiddenApiExemptions {
    init {
        System.loadLibrary("appsandbox_runtime")
    }

    fun install(prefixes: Array<String>): Boolean = nativeSetExemptions(prefixes)

    @JvmStatic private external fun nativeSetExemptions(prefixes: Array<String>): Boolean
}
