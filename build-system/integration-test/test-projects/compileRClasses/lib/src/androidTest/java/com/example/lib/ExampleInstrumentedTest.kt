package com.example.lib

class ExampleInstrumentedTest {
    // access resource from this module
    val a = R.string.from_lib_1

    // access resource from direct impl dependency
    val b = com.example.dependencyLib.R.string.from_lib_2

    // access resource from transitive dependency
    val c = com.example.transitiveDependencyLib.R.string.from_lib_3
}
