/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.ddmlib.testrunner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IShellEnabledDevice;
import com.android.ddmlib.IShellOutputReceiver;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import junit.framework.TestCase;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;

/**
 * Unit tests for {@link RemoteAndroidTestRunner}.
 */
public class RemoteAndroidTestRunnerTest extends TestCase {

    private RemoteAndroidTestRunner mRunner;
    private IShellEnabledDevice mMockDevice;
    private ITestRunListener mMockListener;

    private static final String TEST_PACKAGE = "com.test";
    private static final String TEST_RUNNER = "com.test.InstrumentationTestRunner";

    /**
     * @see junit.framework.TestCase#setUp()
     */
    @Override
    protected void setUp() throws Exception {
        mMockDevice = mock(IShellEnabledDevice.class);
        when(mMockDevice.getName()).thenReturn("serial");
        mMockListener = mock(ITestRunListener.class);
        mRunner = new RemoteAndroidTestRunner(TEST_PACKAGE, TEST_RUNNER, mMockDevice);
    }

    /**
     * Test the basic case building of the instrumentation runner command with no arguments.
     */
    public void testRun() throws Exception {
        String expectedCmd =
                String.format("am instrument -w -r   %s/%s", TEST_PACKAGE, TEST_RUNNER);
        runAndVerify((cmd) -> cmd.equals(expectedCmd));
    }

    /**
     * Test the building of the instrumentation runner command with log set.
     */
    public void testRun_withLog() throws Exception {
        mRunner.setLogOnly(true);
        String expectedCmd = "-e log true";
        runAndVerify((cmd) -> cmd.contains(expectedCmd));
    }

    /**
     * Test the building of the instrumentation runner command with method set.
     */
    public void testRun_withMethod() throws Exception {
        final String className = "FooTest";
        final String testName = "fooTest";
        mRunner.setMethodName(className, testName);
        String expectedCmd = String.format("-e class '%s#%s'", className, testName);
        runAndVerify((cmd) -> cmd.contains(expectedCmd));
    }

    /**
     * Test the building of the instrumentation runner command with test package set.
     */
    public void testRun_withPackage() throws Exception {
        final String packageName = "foo.test";
        mRunner.setTestPackageName(packageName);
        String expectedCmd = String.format("-e package %s", packageName);
        runAndVerify((cmd) -> cmd.contains(expectedCmd));
    }

    /**
     * Test the building of the instrumentation runner command with extra argument added.
     */
    public void testRun_withAddInstrumentationArg() throws Exception {
        final String extraArgName = "blah";
        final String extraArgValue = "blahValue";
        mRunner.addInstrumentationArg(extraArgName, extraArgValue);
        String expectedCmd = String.format("-e %s %s", extraArgName, extraArgValue);
        runAndVerify((cmd) -> cmd.contains(expectedCmd));
    }

    /**
     * Test additional run options.
     */
    public void testRun_runOptions() throws Exception {
        mRunner.setRunOptions("--no-window-animation");
        String expectedCmd =
                String.format(
                        "am instrument -w -r --no-window-animation  %s/%s",
                        TEST_PACKAGE, TEST_RUNNER);
        runAndVerify((cmd) -> cmd.contains(expectedCmd));
    }

    /**
     * Test run when the device throws a IOException
     */
    @SuppressWarnings("unchecked")
    public void testRun_ioException() throws Exception {
        doThrow(IOException.class)
                .when(mMockDevice)
                .executeShellCommand(
                        anyString(),
                        any(IShellOutputReceiver.class),
                        eq(0L),
                        eq(0L),
                        eq(TimeUnit.MILLISECONDS));

        try {
            mRunner.run(mMockListener);
            fail("IOException not thrown");
        } catch (IOException e) {
            // expected
        }
        // verify that the listeners run started, run failure, and run ended methods are called
        verify(mMockListener).testRunStarted(TEST_PACKAGE, 0);
        verify(mMockListener).testRunFailed(anyString());
        verify(mMockListener).testRunEnded(anyLong(), eq(Collections.EMPTY_MAP));
    }

    /**
     * Calls {@link RemoteAndroidTestRunner#run(ITestRunListener...)} and verifies the given
     * <var>expectedCmd</var> pattern was received by the mock device.
     */
    private void runAndVerify(ArgumentMatcher<String> expectedCmd) throws Exception {
        mRunner.run(mMockListener);

        verify(mMockDevice)
                .executeShellCommand(
                        ArgumentMatchers.argThat(expectedCmd),
                        any(IShellOutputReceiver.class),
                        eq(0L),
                        eq(0L),
                        eq(TimeUnit.MILLISECONDS));
    }
}
