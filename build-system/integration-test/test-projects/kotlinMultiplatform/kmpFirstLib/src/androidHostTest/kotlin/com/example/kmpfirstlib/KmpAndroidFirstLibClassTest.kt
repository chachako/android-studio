package com.example.kmpfirstlib

import org.junit.Test
import java.time.LocalDate

class KmpAndroidFirstLibClassTest {

    @Test
    fun testThatPasses() {
        val x = KmpAndroidFirstLibClass()
        assert(x.callCommonLibClass() == x.callAndroidLibClass())
        assert(x.callKmpSecondLibClass() == x.callAndroidLibClass())
        assert(x.callJvmLibClass() == x.callAndroidLibClass())
    }

    @Test
    fun testImplementationDependencyFromCommon() {
        assert(
            com.google.common.math.IntMath.checkedAdd(2, 3) == 5
        )
    }

    @Test
    fun testInternalVisibilityAccess() {
        val x = KmpAndroidFirstLibClass()
        assert(x.callInternalFuncAndroidMain() == "foobar")

        val y = KmpCommonFirstLibClass()
        assert(y.getInternal() == "I'm here")
    }
}
