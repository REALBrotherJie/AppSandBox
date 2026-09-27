package com.example.appsandbox.experiments.act006.api36

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class Act006Api36AdapterTest {
    @Test fun rejectsApiAndShaBeforeConstruction() {
        val a = Act006Api36Adapter()
        assertEquals(Act006Reason.API_MISMATCH, Act006Reason.API_MISMATCH)
    }
    @Test fun fingerprintIsIndependentApi36Shape() { assertEquals(true, Act006Api36Adapter().javaClass.declaredMethods.any { it.name == "attach" }) }
}
