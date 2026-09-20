package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildContractTest {
    @Test
    fun targetSdkContract_isApi36() {
        assertEquals(36, BuildContract.TARGET_SDK)
    }

    @Test
    fun versionName_marksCleanV2() {
        assertEquals("0.1.0-v2", BuildContract.VERSION_NAME)
    }
}
