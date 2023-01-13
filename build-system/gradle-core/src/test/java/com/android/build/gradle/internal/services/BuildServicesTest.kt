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

package com.android.build.gradle.internal.services

import com.android.build.gradle.internal.fixture.TestProjects
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.Serializable
import java.util.*
import javax.annotation.concurrent.NotThreadSafe
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class BuildServicesTest {

    @get:Rule
    val tmpDir = TemporaryFolder()

    private lateinit var project: Project

    @Before
    fun setUp() {
        project = TestProjects.builder(tmpDir.newFolder("project").toPath()).build()
    }

    @Test
    fun `test global BuildService`() {
        // Create a new classloader to load all classes in this test
        val differentClassloader = CustomClassLoader { className: String ->
            className.startsWith(BuildServicesTest::class.java.packageName)
        }

        // Create the global BuildService from the new classloader
        val serviceRegistrationClass = differentClassloader.loadClass(ExampleBuildServiceImpl.RegistrationAction::class.java.name)
        val serviceRegistration = serviceRegistrationClass.getConstructor(Project::class.java).newInstance(project)
        val actualBuildService = (serviceRegistrationClass.getMethod("execute").invoke(serviceRegistration) as Provider<*>).get()

        // Get (a proxy to) the global BuildService from the current classloader
        val buildService = ExampleBuildServiceImpl.RegistrationAction(project).execute().get()

        // Confirm that they are from different classloaders
        assertNotSame(buildService.javaClass.classLoader, actualBuildService.javaClass.classLoader)

        // Check that they are different objects but can access the same global BuildService
        assertNotSame(buildService, actualBuildService)
        assertSame(buildService.uuid, actualBuildService.javaClass.getMethod("getUuid").invoke(actualBuildService))

        // Check that different types of method calls to the global BuildService work
        assertEquals(
            "Returned value from $actualBuildService",
            buildService.methodCallWithBootstrapTypes(123)
        )
        assertEquals(
            "Returned value from $actualBuildService",
            buildService.methodCallWithInterfaceTypes(FooImpl("Some text")).data
        )
        assertEquals(
            "Returned value from $actualBuildService",
            buildService.methodCallWithSerializableTypes(FooSerializable("Some text")).data
        )
    }
}

/**
 * Custom [ClassLoader] that loads a [Class] if the class's name satisfies the given
 * [classNameFilter], otherwise it delegates loading the class to the default (system) class loader.
 */
@NotThreadSafe
private class CustomClassLoader(private val classNameFilter: (String) -> Boolean) : ClassLoader() {

    private val loadedClasses = mutableMapOf<String, Class<*>>()

    override fun loadClass(name: String): Class<*> {
        return if (classNameFilter(name)) {
            loadedClasses.getOrPut(name) {
                val bytes = getResourceAsStream(name.replace(".", "/") + ".class")!!.readAllBytes()
                // Note: The call below might call into `loadClass` again to load another class,
                // which will try to update the `loadedClasses` map while it's being updated. This
                // is not allowed with a ConcurrentHashMap, so we're using a non-thread-safe map in
                // this class. If thread safety is needed, consider adding a reentrant lock (e.g.,
                // `synchronized`).
                defineClass(name, bytes, 0, bytes.size)
            }
        } else {
            super.loadClass(name)
        }
    }
}

interface ExampleBuildService {

    val uuid: UUID

    fun methodCallWithBootstrapTypes(arg: Int): String

    fun methodCallWithInterfaceTypes(arg: FooInterface): BarInterface

    fun methodCallWithSerializableTypes(arg: FooSerializable): BarSerializable

}

abstract class ExampleBuildServiceImpl : BuildService<BuildServiceParameters.None>, ExampleBuildService {

    override val uuid: UUID = UUID.randomUUID()

    override fun methodCallWithBootstrapTypes(arg: Int): String {
        return "Returned value from $this"
    }

    override fun methodCallWithInterfaceTypes(arg: FooInterface): BarInterface {
        return BarImpl("Returned value from $this")
    }

    override fun methodCallWithSerializableTypes(arg: FooSerializable): BarSerializable {
        return BarSerializable("Returned value from $this")
    }

    class RegistrationAction(project: Project)
        : GlobalServiceRegistrationAction<ExampleBuildService, ExampleBuildServiceImpl, BuildServiceParameters.None>(
            project, ExampleBuildService::class.java, ExampleBuildServiceImpl::class.java) {

        override fun configure(parameters: BuildServiceParameters.None) {
            // Do nothing
        }
    }

}

interface FooInterface {
    val data: String
}

data class FooImpl(override val data: String) : FooInterface

interface BarInterface {
    val data: String
}

data class BarImpl(override val data: String): BarInterface

data class FooSerializable(val data: String) : Serializable

data class BarSerializable(val data: String) : Serializable
