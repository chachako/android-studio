/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.build.gradle.integration.common.fixture

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor.ConfigurationCaching
import com.google.common.collect.ImmutableList

/**
 * Options to run Gradle (memory, config caching)
 */
data class GradleOptions(
    val memoryRequirement: MemoryRequirement,
    val configurationCaching: ConfigurationCaching,
) {
    fun mutate(action: GradleOptionBuilderDelegate.() -> Unit): GradleOptions {
        val delegate = GradleOptionBuilderDelegate(this)
        action(delegate)
        return delegate.asGradleOptions
    }
}

/** Policy for setting Heap Size for Gradle process */
data class MemoryRequirement(
    val heap: String = DEFAULT_HEAL,
    val metaspace: String = DEFAULT_METASPACE
) {
    companion object {
        const val DEFAULT_HEAL: String = "1G"
        const val DEFAULT_METASPACE: String = "1G"

        /** use default heap size for gradle. */
        @JvmStatic
        fun useDefault(): MemoryRequirement = use(null, null)

        /**
         * Use a provided heap size for Gradle
         *
         * @param heap the desired heap size
         * @param metaspace the desired metaspace size
         */
        @JvmStatic
        fun use(heap: String?, metaspace: String?): MemoryRequirement {
            return MemoryRequirement(
                heap ?: DEFAULT_HEAL,
                metaspace ?: DEFAULT_METASPACE
            )
        }
    }

    val asJvmArgs: List<String>
        get() = ImmutableList.of("-XX:MaxMetaspaceSize=$metaspace", "-Xmx$heap");
}

/**
 * Interface for builder pattern to configure [GradleOptions]
 */
interface GradleOptionBuilder<T> {

    /**
     * Sets the test heap size requirement. Example values : 1024m, 2048m...
     *
     * @param heapSize the heap size in a format understood by the -Xmx JVM parameter
     * @return itself.
     */
    fun withHeap(heapSize: String?): T

    /**
     * Sets the test metaspace size requirement. Example values : 128m, 1024m...
     *
     * @param metaspaceSize the metaspacesize in a format understood by the -Xmx JVM parameter
     * @return itself.
     */
    fun withMetaspace(metaspaceSize: String?): T

    /**
     * Sets the mode for Configuration Caching
     */
    fun withConfigurationCaching(configurationCaching: ConfigurationCaching): T
}

/**
 * Self-contained implementation of [GradleOptionBuilder]
 */
class GradleOptionBuilderDelegate(
    defaultValues: GradleOptions? = null
): GradleOptionBuilder<GradleOptionBuilderDelegate> {

    private var heap: String? = defaultValues?.memoryRequirement?.heap
    private var metaspace: String? = defaultValues?.memoryRequirement?.metaspace
    private var configCaching: ConfigurationCaching = defaultValues?.configurationCaching ?: ConfigurationCaching.PROJECT_ISOLATION

    override fun withHeap(heapSize: String?): GradleOptionBuilderDelegate {
        heap = heapSize
        return this
    }

    override fun withMetaspace(metaspaceSize: String?): GradleOptionBuilderDelegate {
        metaspace = metaspaceSize
        return this
    }

    override fun withConfigurationCaching(configurationCaching: ConfigurationCaching): GradleOptionBuilderDelegate {
        configCaching = configurationCaching
        return this
    }

    val asGradleOptions: GradleOptions
        get() = GradleOptions(
            MemoryRequirement.use(heap, metaspace),
            configCaching
        )
}
