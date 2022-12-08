package com.example.android.kotlin

import java.util.logging.Logger.getLogger

class DynamicFeature1 () {
    companion object {
        fun stubDynamicFeature1FuncForTestingCodeCoverage() {
            getLogger("DynamicFeature1").info("stubDynamicFeature1FuncForTestingCodeCoverage()")
        }
    }
}
