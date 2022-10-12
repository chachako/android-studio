package com.android.tools.deploy.liveedit

interface A {
    fun doWork(x: Long) : Long = x + 10
    fun doOtherWork(x: Long) : Long
}

interface B : A {
    override fun doOtherWork(x: Long) = x * 10

    fun doClassWork(x: Long) : Long
}

class C : B {

    override fun doClassWork(x: Long) : Long {
        return x + x
    }

}

fun test() : Long {
    val x = C()
    return x.doWork(1) + x.doOtherWork(1) + x.doClassWork(1)
}
