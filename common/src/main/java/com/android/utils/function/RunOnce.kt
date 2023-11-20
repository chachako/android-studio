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
package com.android.utils.function

import com.google.common.base.Suppliers
import java.util.function.Supplier

/**
 * Utility class for creating code that runs at most once without having to worry about the
 * awkwardness of explicitly using a [Supplier] that returns [Unit].
 *
 * This is a `value` class, so it is represented internally identically to using the Supplier
 * directly, but provides some type-sense as well as hiding any potential static analysis
 * issues that arise from using a [Supplier] but ignoring the return value of [Supplier.get].
 */
@JvmInline
value class RunOnce private constructor(val supplier: Supplier<Unit>) : () -> Unit {
  constructor(block: () -> Unit) : this(Suppliers.memoize(block::invoke))

  override fun invoke() {
    supplier.get()
  }
}
