/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.ddmlib.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import junit.framework.TestCase;

public class BatteryFetcherTest extends TestCase {

    /**
     * Test that getBattery works as expected when queries made in different states.
     */
    public void testGetBattery() throws Exception {
        IDevice mockDevice = DeviceTest.createMockDevice2();
        when(mockDevice.getProperty("ro.product.model")).thenReturn("Pixel 3");
        DeviceTest.injectShellResponse2(mockDevice, 50, "20\r\n");
        DeviceTest.injectShellResponse2(mockDevice, 50, "20\r\n");

        BatteryFetcher fetcher = new BatteryFetcher(mockDevice);
        // do query in unpopulated state
        Future<Integer> uncachedFuture = fetcher.getBattery(0, TimeUnit.MILLISECONDS);
        // do query in fetching state
        Future<Integer> fetchingFuture = fetcher.getBattery(0, TimeUnit.MILLISECONDS);
        // do query in fetching state

        assertEquals(20, uncachedFuture.get().intValue());
        // do queries with short timeout to ensure battery already available
        assertEquals(20, fetchingFuture.get(1, TimeUnit.MILLISECONDS).intValue());
        assertEquals(20,
                fetcher.getBattery(1, TimeUnit.SECONDS).get(1, TimeUnit.MILLISECONDS).intValue());
        verify(mockDevice).getProperty("ro.product.model");
    }

    /**
     * Test that getBattery returns exception when battery checks return invalid data.
     */
    public void testGetBattery_badResponse() throws Exception {
        IDevice mockDevice = DeviceTest.createMockDevice2();
        when(mockDevice.getProperty("ro.product.model")).thenReturn("Pixel 3 XL");
        DeviceTest.injectShellResponse2(mockDevice, 50, "blargh");
        DeviceTest.injectShellResponse2(mockDevice, 50, "blargh");

        BatteryFetcher fetcher = new BatteryFetcher(mockDevice);
        try {
            fetcher.getBattery(0, TimeUnit.MILLISECONDS).get();
            fail("ExecutionException not thrown");
        } catch (ExecutionException e) {
            // expected
            assertTrue(e.getCause() instanceof IOException);
        }
        verify(mockDevice).getProperty("ro.product.model");
    }

    /** Test that getBattery propagates executeShell exceptions. */
    public void testGetBattery_shellException() throws Exception {
        IDevice mockDevice = DeviceTest.createMockDevice2();
        when(mockDevice.getProperty("ro.product.model")).thenReturn("fakeValue");
        doThrow(new ShellCommandUnresponsiveException())
                .when(mockDevice)
                .executeShellCommand(any(), any(), anyLong(), any());

        BatteryFetcher fetcher = new BatteryFetcher(mockDevice);
        try {
            fetcher.getBattery(0, TimeUnit.MILLISECONDS).get();
            fail("ExecutionException not thrown");
        } catch (ExecutionException e) {
            // expected
            assertTrue(e.getCause() instanceof ShellCommandUnresponsiveException);
        }
    }

    /**
     * Checks that getBattery propagates a thrown Error.
     */
    public void testGetBattery_AssertionError() throws Exception {
        IDevice mockDevice = DeviceTest.createMockDevice2();
        doThrow(new AssertionError())
                .when(mockDevice)
                .executeShellCommand(any(), any(), anyLong(), any());

        BatteryFetcher fetcher = new BatteryFetcher(mockDevice);
        try {
            fetcher.getBattery(0, TimeUnit.MILLISECONDS).get();
            fail("ExecutionException not thrown");
        } catch (ExecutionException e) {
            // expected
            assertTrue(e.getCause() instanceof AssertionError);
        }
        verify(mockDevice).executeShellCommand(any(), any(), anyLong(), any());
    }
}
