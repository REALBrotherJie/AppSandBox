package com.example.appsandbox.experiments.act008

import org.junit.Assert.*
import org.junit.Test

class Act008RequestLedgerTest {
    @Test fun exactReplayIsIdempotentButIdentityReuseIsRejected() {
        val ledger = Act008RequestLedger(2)
        assertNull(ledger.check("request", "operation", "run", "instance"))
        assertTrue(ledger.check("request", "operation", "run", "instance") == true)
        assertTrue(ledger.check("request", "other", "run", "instance") == false)
    }

    @Test fun ledgerIsBounded() {
        val ledger = Act008RequestLedger(1)
        assertNull(ledger.check("a", "op", "run", "instance"))
        assertNull(ledger.check("b", "op", "run", "instance"))
        assertNull(ledger.check("a", "op", "run", "instance"))
    }
}
