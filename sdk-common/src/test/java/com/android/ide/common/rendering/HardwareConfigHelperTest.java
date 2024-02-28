package com.android.ide.common.rendering;

import static com.android.ide.common.rendering.HardwareConfigHelper.getGenericLabel;
import static com.android.ide.common.rendering.HardwareConfigHelper.getNexusLabel;
import static com.android.ide.common.rendering.HardwareConfigHelper.getNexusMenuLabel;
import static com.android.ide.common.rendering.HardwareConfigHelper.isAutomotive;
import static com.android.ide.common.rendering.HardwareConfigHelper.isDesktop;
import static com.android.ide.common.rendering.HardwareConfigHelper.isGeneric;
import static com.android.ide.common.rendering.HardwareConfigHelper.isMobile;
import static com.android.ide.common.rendering.HardwareConfigHelper.isNexus;
import static com.android.ide.common.rendering.HardwareConfigHelper.isTv;
import static com.android.ide.common.rendering.HardwareConfigHelper.isWear;
import static com.android.ide.common.rendering.HardwareConfigHelper.sortNexusListByRank;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.android.prefs.AndroidLocationsSingleton;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.DeviceManager;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.utils.StdLogger;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public final class HardwareConfigHelperTest {
    private static DeviceManager getDeviceManager() {
        return DeviceManager.createInstance(
                AndroidSdkHandler.getInstance(AndroidLocationsSingleton.INSTANCE, null),
                new StdLogger(StdLogger.Level.INFO));
    }

    @Test
    public void nexus() {
        DeviceManager deviceManager = getDeviceManager();
        Device n1 = deviceManager.getDevice("Nexus One", "Google");
        assertNotNull(n1);
        assertEquals("Nexus One", n1.getId());
        //noinspection deprecation
        assertSame(n1.getId(), n1.getName());
        assertEquals("Nexus One", n1.getDisplayName());
        assertTrue(isNexus(n1));

        assertEquals("Nexus One (3.7\", 480 × 800, hdpi)", getNexusLabel(n1));
        assertEquals("3.7, 480 × 800, hdpi (Nexus One)", getNexusMenuLabel(n1));
        assertFalse(isGeneric(n1));
    }

    @Test
    public void nexus7() {
        DeviceManager deviceManager = getDeviceManager();
        Device n7 = deviceManager.getDevice("Nexus 7", "Google");
        Device n7b = deviceManager.getDevice("Nexus 7 2013", "Google");
        assertNotNull(n7);
        assertNotNull(n7b);
        assertEquals("Nexus 7 (2012)", n7.getDisplayName());
        assertEquals("Nexus 7", n7b.getDisplayName());

        assertEquals("Nexus 7 (2012) (7.0\", 800 × 1280, tvdpi)", getNexusLabel(n7));
        assertEquals("7.0, 800 × 1280, tvdpi (Nexus 7 2012)", getNexusMenuLabel(n7));

        assertTrue(isNexus(n7));
        assertTrue(isNexus(n7b));
        assertTrue(HardwareConfigHelper.nexusRank(n7b) > HardwareConfigHelper.nexusRank(n7));

        assertEquals("Nexus 7 (2012) (7.0\", 800 × 1280, tvdpi)", getNexusLabel(n7));
        assertEquals("Nexus 7 (7.0\", 1200 × 1920, xhdpi)", getNexusLabel(n7b));
        assertFalse(isGeneric(n7));
    }

    @Test
    public void generic() {
        DeviceManager deviceManager = getDeviceManager();
        Device qvga = deviceManager.getDevice("2.7in QVGA", "Generic");
        assertNotNull(qvga);
        assertEquals("2.7\" QVGA", qvga.getDisplayName());
        assertEquals("2.7in QVGA", qvga.getId());
        assertFalse(isNexus(qvga));
        assertEquals(" 2.7\" QVGA (240 × 320, ldpi)", getGenericLabel(qvga));
        assertTrue(isGeneric(qvga));
        assertFalse(isNexus(qvga));
    }

    @Test
    public void isWearIsTvIsRound() {
        DeviceManager deviceManager = getDeviceManager();
        Device qvga = deviceManager.getDevice("2.7in QVGA", "Generic");
        assertNotNull(qvga);
        assertFalse(isWear(qvga));
        assertFalse(isTv(qvga));
        assertTrue(isMobile(qvga));
        assertFalse(isAutomotive(qvga));
        assertFalse(qvga.isScreenRound());

        Device nexus5 = deviceManager.getDevice("Nexus 5", "Google");
        assertNotNull(nexus5);
        assertFalse(isWear(nexus5));
        assertFalse(isTv(nexus5));
        assertTrue(isMobile(nexus5));
        assertFalse(isAutomotive(nexus5));
        assertFalse(nexus5.isScreenRound());

        Device square = deviceManager.getDevice("wearos_square", "Google");
        assertNotNull(square);
        assertTrue(isWear(square));
        assertFalse(square.isScreenRound());
        assertFalse(isTv(square));
        assertFalse(isMobile(square));
        assertFalse(isAutomotive(square));

        Device round = deviceManager.getDevice("wearos_small_round", "Google");
        assertNotNull(round);
        assertTrue(isWear(round));
        assertTrue(round.isScreenRound());
        assertFalse(isTv(round));
        assertFalse(isMobile(round));
        assertFalse(isAutomotive(round));
        assertEquals("Wear OS Small Round (1.2\", 384 × 384, xhdpi)", getNexusLabel(round));
        assertEquals("384 × 384, xhdpi (Small Round)", getNexusMenuLabel(round));

        Device tv1080p = deviceManager.getDevice("tv_1080p", "Google");
        assertNotNull(tv1080p);
        assertTrue(isTv(tv1080p));
        assertFalse(isWear(tv1080p));
        assertFalse(isMobile(tv1080p));
        assertFalse(isAutomotive(tv1080p));
        assertFalse(tv1080p.isScreenRound());
        assertEquals("Television (1080p) (55.0\", 1920 × 1080, xhdpi)", getNexusLabel(tv1080p));
        assertEquals("1080p, 1920 × 1080, xhdpi (TV)", getNexusMenuLabel(tv1080p));

        Device tv720p = deviceManager.getDevice("tv_720p", "Google");
        assertNotNull(tv720p);
        assertFalse(isWear(tv720p));
        assertTrue(isTv(tv720p));
        assertFalse(isMobile(tv720p));
        assertFalse(isAutomotive(tv720p));
        assertFalse(tv720p.isScreenRound());

        Device tv4k = deviceManager.getDevice("tv_4k", "Google");
        assertNotNull(tv4k);
        assertFalse(isWear(tv4k));
        assertTrue(isTv(tv4k));
        assertFalse(isMobile(tv4k));
        assertFalse(isAutomotive(tv4k));
        assertFalse(tv4k.isScreenRound());
    }

    @Test
    public void nexusRank() {
        List<Device> devices = Lists.newArrayList();
        DeviceManager deviceManager = getDeviceManager();
        for (String id : new String[] { "Nexus 7 2013", "Nexus 5", "Nexus 10", "Nexus 4", "Nexus 7",
                                        "Galaxy Nexus", "Nexus S", "Nexus One"}) {
            Device device = deviceManager.getDevice(id, "Google");
            assertNotNull(device);
            devices.add(device);
        }
        sortNexusListByRank(devices);
        Collections.reverse(devices);
        List<String> ids = Lists.newArrayList();
        for (Device device : devices) {
            ids.add(device.getId());
        }
        assertEquals(Arrays.asList(
                "Nexus One",
                "Nexus S",
                "Galaxy Nexus",
                "Nexus 7",
                "Nexus 10",
                "Nexus 4",
                "Nexus 7 2013",
                "Nexus 5"
        ), ids);
    }

    @Test
    public void screenSize() {
        List<Device> devices = Lists.newArrayList();
        DeviceManager deviceManager = getDeviceManager();
        for (String id : new String[] { "Nexus 7 2013", "Nexus 5", "Nexus 10"}) {
            Device device = deviceManager.getDevice(id, "Google");
            assertNotNull(device);
            devices.add(device);
        }
        HardwareConfigHelper.sortDevicesByScreenSize(devices);
        List<String> ids = Lists.newArrayList();
        for (Device device : devices) {
            ids.add(device.getId());
        }
        assertEquals(Arrays.asList(
                "Nexus 5",
                "Nexus 7 2013",
                "Nexus 10"
        ), ids);
    }

    @Test
    public void automotiveGeneric() {
        List<String> device =
                ImmutableList.of(
                        "automotive_1024p_landscape",
                        "automotive_1080p_landscape",
                        "automotive_portrait",
                        "automotive_distant_display",
                        "automotive_ultrawide",
                        "automotive_large_portrait");
        List<String> label =
                ImmutableList.of(
                        "Automotive (1024p landscape) (1024 × 768, mdpi)",
                        "Automotive (1080p landscape) (1080 × 600, ldpi)",
                        "Automotive Portrait (800 × 1280, ldpi)",
                        "Automotive Distant Display (1080 × 600, ldpi)",
                        "Automotive Ultrawide (3904 × 1320, hdpi)",
                        "Automotive Large Portrait (1280 × 1606, mdpi)");

        DeviceManager deviceManager = getDeviceManager();
        for (int i = 0; i < device.size(); i++) {
            Device automotive = deviceManager.getDevice(device.get(i), "Google");
            assertNotNull(automotive);
            assertFalse(isWear(automotive));
            assertFalse(isTv(automotive));
            assertFalse(isMobile(automotive));
            assertTrue(isAutomotive(automotive));
            assertFalse(automotive.isScreenRound());
            assertFalse(isGeneric(automotive));
            assertEquals(label.get(i), getGenericLabel(automotive));
        }
    }

    @Test
    public void desktopGeneric() {
        DeviceManager deviceManager = getDeviceManager();
        Device desktop_medium = deviceManager.getDevice("desktop_medium", "Google");
        assertNotNull(desktop_medium);
        assertFalse(isWear(desktop_medium));
        assertFalse(isTv(desktop_medium));
        assertFalse(isMobile(desktop_medium));
        assertFalse(isAutomotive(desktop_medium));
        assertTrue(isDesktop(desktop_medium));
        assertFalse(desktop_medium.isScreenRound());
        assertFalse(isGeneric(desktop_medium));
        assertEquals("Medium Desktop (3840 × 2160, xhdpi)", getGenericLabel(desktop_medium));
    }
}
