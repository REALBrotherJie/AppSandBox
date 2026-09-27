package com.example.appsandbox.experiments.act006.api31

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Act006Api31AdapterTest {
    @Test fun fingerprintPinsCompleteApi31AttachSignature() {
        val value = Act006Api31Adapter().fingerprint
        assertTrue(value.endsWith("@31")); assertTrue(value.contains("ActivityConfigCallback"))
    }

    @Test fun resultDefaultsProveNoCalls() {
        val result = Act006AttachResult("REJECTED", "STALE_REVISION")
        assertFalse(result.classLoaded); assertFalse(result.constructed); assertFalse(result.attachInvokeAttempted)
        assertFalse(result.attachCompleted); assertFalse(result.lifecycle)
    }
}
