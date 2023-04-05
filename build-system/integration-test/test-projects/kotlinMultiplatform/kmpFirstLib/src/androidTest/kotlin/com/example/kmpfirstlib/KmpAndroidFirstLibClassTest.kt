package com.example.kmpfirstlib

import org.junit.Test

class KmpAndroidFirstLibClassTest {

    @Test
    fun testThatPasses() {
        val x = KmpAndroidFirstLibClass()
        assert(x.callCommonLibClass() == x.callAndroidLibClass())
        assert(x.callKmpSecondLibClass() == x.callAndroidLibClass())
        assert(x.callJvmLibClass() == x.callAndroidLibClass())
    }
}
