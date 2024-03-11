package com.android.tools.appinspection.common

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * A delegate wrapper for [ThreadLocal]
 *
 * Usage:
 * ```
 *  val data by threadLocal { "initial-value" }
 * ```
 */
class ThreadLocalDelegate<T>(initialValue: () -> T) : ReadWriteProperty<Any?, T> {
  private val threadLocal = ThreadLocal.withInitial { initialValue() }

  override operator fun getValue(thisRef: Any?, property: KProperty<*>): T = threadLocal.get()

  override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
    threadLocal.set(value)
  }
}

fun <T> threadLocal(initialValue: () -> T) = ThreadLocalDelegate(initialValue)
