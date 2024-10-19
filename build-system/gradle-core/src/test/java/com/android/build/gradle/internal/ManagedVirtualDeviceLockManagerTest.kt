/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.internal

import com.android.builder.utils.SynchronizedFile
import com.android.prefs.AndroidLocationsProvider
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.junit.MockitoRule
import org.mockito.junit.MockitoJUnit
import java.lang.Thread.UncaughtExceptionHandler
import java.io.File
import java.util.concurrent.Executors.newCachedThreadPool
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

@RunWith(JUnit4::class)
class ManagedVirtualDeviceLockManagerTest {

    @get:Rule
    val mockitoRule: MockitoRule = MockitoJUnit.rule()

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var avdFolder: File

    private val executorService = newCachedThreadPool()

    private val androidLocations: AndroidLocationsProvider = mock()

    private lateinit var trackedFile: File

    @Before
    fun setup() {
        avdFolder = tmpFolder.newFolder()
        whenever(androidLocations.gradleAvdLocation).thenReturn(avdFolder.toPath())

        trackedFile = avdFolder.resolve("active_gradle_devices")
    }

    @Test
    fun lock_basicLockUpdatesTrackingCorrectly() {
        val lockManager = ManagedVirtualDeviceLockManager(androidLocations, 1, 0L)

        lockManager.lock().use {
            // We can go ahead and read the file here to assure it keeps track of the lock number
            assertThat(trackedFile).exists()

            assertThat(trackedFile).contains("MDLockCount 1")
            assertThat(lockManager.devicesInProcess).isEqualTo(1)
        }

        // the lock number should be returned after the lock is closed.
        assertThat(trackedFile).exists()
        assertThat(trackedFile).contains("MDLockCount 0")
        assertThat(lockManager.devicesInProcess).isEqualTo(0)
    }

    @Test
    fun lock_multipleLocksCanRunSimultaneously() {
        val lockManager = ManagedVirtualDeviceLockManager(androidLocations, 2, 0L)

        lockManager.lock().use {
            assertThat(trackedFile).exists()

            assertThat(trackedFile).contains("MDLockCount 1")
            assertThat(lockManager.devicesInProcess).isEqualTo(1)

            // Run two locks concurrently.
            val thread = executorService.submit() {
                lockManager.lock().use {

                    assertThat(trackedFile).contains("MDLockCount 2")
                    assertThat(lockManager.devicesInProcess).isEqualTo(2)
                }
            }

            // This should not take long or be expensive. So we shouldn't have a long timeout
            thread.get(200, TimeUnit.MILLISECONDS)

            // The lock from the second run should be released
            assertThat(trackedFile).exists()

            assertThat(trackedFile).contains("MDLockCount 1")
            assertThat(lockManager.devicesInProcess).isEqualTo(1)
        }

        assertThat(trackedFile).exists()

        assertThat(trackedFile).contains("MDLockCount 0")
        assertThat(lockManager.devicesInProcess).isEqualTo(0)
    }

    @Test
    fun lock_multipleLocksBlockCorrectly() {
        val lockManager = ManagedVirtualDeviceLockManager(androidLocations, 1, 0L)

        lateinit var thread: Future<*>

        lockManager.lock().use {
            assertThat(trackedFile).exists()

            assertThat(trackedFile).contains("MDLockCount 1")
            assertThat(lockManager.devicesInProcess).isEqualTo(1)

            // Attempt to run the second lock
            thread = executorService.submit() {
                lockManager.lock().use {

                    // When it eventually runs the lock count should only be 1
                    assertThat(trackedFile).contains("MDLockCount 1")
                    assertThat(lockManager.devicesInProcess).isEqualTo(1)
                }
            }

            // Timeout doesn't matter, since the underlying thread won't complete
            assertThrows(TimeoutException::class.java) {
                thread.get(200, TimeUnit.MILLISECONDS)
            }
        }

        // Now the thread should complete fine.
        thread.get(200, TimeUnit.MILLISECONDS)

        assertThat(lockManager.devicesInProcess).isEqualTo(0)
    }

    @Test
    fun lock_worksWhenMultipleRequested() {
        val lockManager = ManagedVirtualDeviceLockManager(androidLocations, 8, 0L)

        // Attempt to grab most of the locks
        lockManager.lock(6).use { lock ->
            assertThat(lock.lockCount).isEqualTo(6)

            assertThat(trackedFile).exists()

            assertThat(trackedFile).contains("MDLockCount 6")
            assertThat(lockManager.devicesInProcess).isEqualTo(6)
        }

        // Expect all locks to be returned
        assertThat(trackedFile).exists()

        assertThat(trackedFile).contains("MDLockCount 0")
        assertThat(lockManager.devicesInProcess).isEqualTo(0)

        // Attempt to grab all of the locks
        lockManager.lock(8).use { lock ->
            assertThat(lock.lockCount).isEqualTo(8)

            assertThat(trackedFile).exists()

            assertThat(trackedFile).contains("MDLockCount 8")
        }

        // Expect all locks to be returned
        assertThat(trackedFile).exists()

        assertThat(trackedFile).contains("MDLockCount 0")
        assertThat(lockManager.devicesInProcess).isEqualTo(0)

        // Attempt to grab more than are available
        lockManager.lock(20).use { lock ->
            assertThat(lock.lockCount).isEqualTo(8)

            assertThat(trackedFile).exists()

            assertThat(trackedFile).contains("MDLockCount 8")
            assertThat(lockManager.devicesInProcess).isEqualTo(8)
        }

        // Expect all locks to be returned
        assertThat(trackedFile).exists()

        assertThat(trackedFile).contains("MDLockCount 0")
        assertThat(lockManager.devicesInProcess).isEqualTo(0)
    }

    @Test
    fun lock_simultaneousLockManagers() {
        // used to simulate two lock managers from separate gradle instances interacting at the
        // same time.
        val lockManager1 = ManagedVirtualDeviceLockManager(androidLocations, 8, 0L)
        val lockManager2 = ManagedVirtualDeviceLockManager(androidLocations, 8, 0L)

        lateinit var thread: Future<*>

        // Grab  a lock from the first manager
        lockManager1.lock().use { lock1 ->
            assertThat(lock1.lockCount).isEqualTo(1)

            assertThat(trackedFile).exists()

            assertThat(trackedFile).contains("MDLockCount 1")
            assertThat(lockManager1.devicesInProcess).isEqualTo(1)
            assertThat(lockManager2.devicesInProcess).isEqualTo(0)

            // Attempt to run the second lock
            thread = executorService.submit() {
                lockManager2.lock().use { lock2 ->
                    assertThat(lock2.lockCount).isEqualTo(1)

                    assertThat(trackedFile).contains("MDLockCount 2")
                    assertThat(lockManager1.devicesInProcess).isEqualTo(1)
                    assertThat(lockManager2.devicesInProcess).isEqualTo(1)
                }
            }

            // This should not take long or be expensive. So we shouldn't have a long timeout
            thread.get(200, TimeUnit.MILLISECONDS)

            assertThat(trackedFile).contains("MDLockCount 1")
            assertThat(lockManager1.devicesInProcess).isEqualTo(1)
            assertThat(lockManager2.devicesInProcess).isEqualTo(0)
        }

        assertThat(trackedFile).contains("MDLockCount 0")
        assertThat(lockManager1.devicesInProcess).isEqualTo(0)
        assertThat(lockManager2.devicesInProcess).isEqualTo(0)
    }

    @Test
    fun lock_simultaneousLockManagersDifferentMaximums() {
        // used to simulate two lock managers from separate gradle instances interacting at the
        // same time. They have different maximum concurrent devices 6 and 9 respectively.
        val lockManager1 = ManagedVirtualDeviceLockManager(androidLocations, 6, 0L)
        val lockManager2 = ManagedVirtualDeviceLockManager(androidLocations, 9, 0L)

        lateinit var thread: Future<*>

        // Grab  5 lock from the first manager
        lockManager1.lock(5).use { lock1 ->
            assertThat(lock1.lockCount).isEqualTo(5)

            assertThat(trackedFile).exists()

            assertThat(trackedFile).contains("MDLockCount 5")
            assertThat(lockManager1.devicesInProcess).isEqualTo(5)
            assertThat(lockManager2.devicesInProcess).isEqualTo(0)

            // Attempt to grab 3 locks from second, since the second manager has a max of 9
            // this is fine.
            thread = executorService.submit() {
                lockManager2.lock(3).use { lock2 ->
                    assertThat(lock2.lockCount).isEqualTo(3)

                    assertThat(trackedFile).contains("MDLockCount 8")
                    assertThat(lockManager1.devicesInProcess).isEqualTo(5)
                    assertThat(lockManager2.devicesInProcess).isEqualTo(3)
                }
            }

            // This should not take long or be expensive. So we shouldn't have a long timeout
            thread.get(200, TimeUnit.MILLISECONDS)

            assertThat(trackedFile).contains("MDLockCount 5")
            assertThat(lockManager1.devicesInProcess).isEqualTo(5)
            assertThat(lockManager2.devicesInProcess).isEqualTo(0)
        }

        assertThat(trackedFile).contains("MDLockCount 0")
        assertThat(lockManager1.devicesInProcess).isEqualTo(0)
        assertThat(lockManager2.devicesInProcess).isEqualTo(0)

        // Now go the other way, grab 5 locks from the second manager
        lockManager2.lock(5).use { lock2 ->
            assertThat(lock2.lockCount).isEqualTo(5)

            assertThat(trackedFile).exists()

            assertThat(trackedFile).contains("MDLockCount 5")
            assertThat(lockManager1.devicesInProcess).isEqualTo(0)
            assertThat(lockManager2.devicesInProcess).isEqualTo(5)

            // Attempt to grab 3 locks from the first, it will only allocate 1, as the maximum
            // for the first manager is 6, and only 1 is left available.
            thread = executorService.submit() {
                lockManager1.lock(3).use { lock1 ->
                    assertThat(lock1.lockCount).isEqualTo(1)

                    assertThat(trackedFile).contains("MDLockCount 6")
                    assertThat(lockManager1.devicesInProcess).isEqualTo(1)
                    assertThat(lockManager2.devicesInProcess).isEqualTo(5)
                }
            }

            // This should not take long or be expensive. So we shouldn't have a long timeout
            thread.get(200, TimeUnit.MILLISECONDS)

            assertThat(trackedFile).contains("MDLockCount 5")
            assertThat(lockManager1.devicesInProcess).isEqualTo(0)
            assertThat(lockManager2.devicesInProcess).isEqualTo(5)
        }

        assertThat(trackedFile).contains("MDLockCount 0")
        assertThat(lockManager1.devicesInProcess).isEqualTo(0)
        assertThat(lockManager2.devicesInProcess).isEqualTo(0)
    }

    @Test
    fun lock_simultaneousLockManagersDoesNotFailOnOverAllocation() {
        // used to simulate two lock managers from separate gradle instances interacting at the
        // same time. They have different maximum concurrent devices 2 and 5 respectively.
        val lockManager1 = ManagedVirtualDeviceLockManager(androidLocations, 2, 0L)
        val lockManager2 = ManagedVirtualDeviceLockManager(androidLocations, 5, 0L)

        lateinit var thread: Future<*>

        lockManager2.lock(4).use {
            assertThat(trackedFile).exists()

            assertThat(trackedFile).contains("MDLockCount 4")
            assertThat(lockManager1.devicesInProcess).isEqualTo(0)
            assertThat(lockManager2.devicesInProcess).isEqualTo(4)

            // Attempt to grab a lock for manager 1. Since 4 are already allocated and manager 1
            // has a max of 2, this will wait to execute.
            thread = executorService.submit() {
                lockManager1.lock().use {

                    // When it eventually runs the lock count should only be 1
                    // the locks for the second manager should already be freed.
                    assertThat(trackedFile).contains("MDLockCount 1")
                    assertThat(lockManager1.devicesInProcess).isEqualTo(1)
                    assertThat(lockManager2.devicesInProcess).isEqualTo(0)
                }
            }

            // Timeout doesn't matter, since the underlying thread won't complete
            assertThrows(TimeoutException::class.java) {
                thread.get(200, TimeUnit.MILLISECONDS)
            }
        }

        // Now the thread should complete fine.
        thread.get(200, TimeUnit.MILLISECONDS)

        assertThat(trackedFile).contains("MDLockCount 0")
        assertThat(lockManager1.devicesInProcess).isEqualTo(0)
        assertThat(lockManager2.devicesInProcess).isEqualTo(0)
    }

    @Test
    fun executeShutdown_shutdownReleasesLocks() {

        val lockManager1 = ManagedVirtualDeviceLockManager(androidLocations, 3, 0L)
        val lockManager2 = ManagedVirtualDeviceLockManager(androidLocations, 3, 0L)

        lockManager1.lock().use {
            assertThat(trackedFile).exists()

            assertThat(trackedFile).contains("MDLockCount 1")
            assertThat(lockManager1.devicesInProcess).isEqualTo(1)

            // Note: This lock will not be closed. We have 1 stale device.
            lockManager1.lock()

            assertThat(trackedFile).contains("MDLockCount 2")
            assertThat(lockManager1.devicesInProcess).isEqualTo(2)
        }

        assertThat(trackedFile).contains("MDLockCount 1")
        assertThat(lockManager1.devicesInProcess).isEqualTo(1)

        // Clean up any stale devices with shutdown hook.
        lockManager1.executeShutdown()

        assertThat(trackedFile).contains("MDLockCount 0")
        assertThat(lockManager1.devicesInProcess).isEqualTo(0)

        lockManager2.lock().use {
            assertThat(trackedFile).exists()

            assertThat(trackedFile).contains("MDLockCount 1")
            assertThat(lockManager1.devicesInProcess).isEqualTo(0)
            assertThat(lockManager2.devicesInProcess).isEqualTo(1)

            lockManager1.lock().use {

                assertThat(trackedFile).exists()

                assertThat(trackedFile).contains("MDLockCount 2")
                assertThat(lockManager1.devicesInProcess).isEqualTo(1)
                assertThat(lockManager2.devicesInProcess).isEqualTo(1)

                // Note: This lock will not be closed. We again have 1 stale device.
                lockManager1.lock()

                assertThat(trackedFile).contains("MDLockCount 3")
                assertThat(lockManager1.devicesInProcess).isEqualTo(2)
                assertThat(lockManager2.devicesInProcess).isEqualTo(1)

                // Clean up all device locks asociated with lcokManager1 with shutdown hook.
                lockManager1.executeShutdown()

                // Should still have 1 lock associated with lockManager2
                assertThat(trackedFile).contains("MDLockCount 1")
                assertThat(lockManager1.devicesInProcess).isEqualTo(0)
                assertThat(lockManager2.devicesInProcess).isEqualTo(1)
            }

            // closing the device lock after a shutdown (which should not occur anyway) should
            // not result in more devices being deallocated.

            assertThat(trackedFile).contains("MDLockCount 1")
            assertThat(lockManager1.devicesInProcess).isEqualTo(0)
            assertThat(lockManager2.devicesInProcess).isEqualTo(1)
        }

        assertThat(trackedFile).contains("MDLockCount 0")
        assertThat(lockManager1.devicesInProcess).isEqualTo(0)
        assertThat(lockManager2.devicesInProcess).isEqualTo(0)
    }
}
