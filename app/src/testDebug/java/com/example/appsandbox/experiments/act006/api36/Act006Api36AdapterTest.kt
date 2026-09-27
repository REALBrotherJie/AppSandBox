package com.example.appsandbox.experiments.act006.api36

import org.junit.Assert.assertEquals
import org.junit.Test

class Act006Api36AdapterTest {
    @Test fun resultDefaultsProveNoCallsOccurred() {
        val result = Act006Result(Act006Reason.ACCESS_DENIED)
        assertEquals(false, result.constructorAttempted)
        assertEquals(false, result.attachInvokeAttempted)
        assertEquals(0, result.lifecycleCalls)
    }
    @Test fun adapterPublishesSingleControlledExecutor() { assertEquals(1, Act006Api36Adapter().javaClass.declaredMethods.count { it.name == "execute" }) }
}
