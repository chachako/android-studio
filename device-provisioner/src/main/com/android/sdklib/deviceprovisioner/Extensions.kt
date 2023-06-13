/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.sdklib.deviceprovisioner

import kotlin.reflect.KClass

/** Marker interface for interfaces that are intended for use as extensions. */
interface Extension

/** A factory method for extensions of [BaseT] of type [T]. */
interface ExtensionProvider<BaseT, T : Extension> {
  val extensionClass: Class<T>

  fun createExtension(base: BaseT): T?
}

/** Convenience method for implementing ExtensionProvider for a class type. */
infix fun <T : Extension, BaseT> KClass<T>.providedBy(provider: (BaseT) -> T?) =
  object : ExtensionProvider<BaseT, T> {
    override val extensionClass: Class<T>
      get() = this@providedBy.java

    override fun createExtension(base: BaseT): T? = provider(base)
  }

interface Extensible {
  fun <T : Extension> extension(extensionClass: Class<T>): T? = null
}

inline fun <reified T : Extension> Extensible.extension() = extension(T::class.java)

/**
 * A registry of [ExtensionProvider] for a particular base instance. Extensions are initialized
 * lazily when requested.
 */
class ExtensionRegistry<BaseT>(base: BaseT, extensionProviders: List<ExtensionProvider<BaseT, *>>) :
  Extensible {
  constructor(
    base: BaseT,
    vararg extensionProviders: ExtensionProvider<BaseT, *>,
  ) : this(base, extensionProviders.toList())

  private val extensions: Map<Class<out Extension>, Lazy<Extension?>> =
    extensionProviders.associate { it.extensionClass to lazy { it.createExtension(base) } }

  override fun <T : Extension> extension(extensionClass: Class<T>): T? {
    @Suppress("UNCHECKED_CAST") return extensions[extensionClass]?.value as T?
  }
}
