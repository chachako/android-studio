/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.sdklib.internal.avd;

import static com.android.sdklib.internal.avd.ConfigKey.ENCODING;
import static com.android.sdklib.internal.avd.SdCards.SDCARD_MIN_BYTE_SIZE;
import static com.android.sdklib.internal.avd.UserSettingsKey.PREFERRED_ABI;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.io.CancellableFileIo;
import com.android.prefs.AbstractAndroidLocations;
import com.android.prefs.AndroidLocationsException;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.PathFileWrapper;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.DeviceManager;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.testutils.MockLog;
import com.android.testutils.file.InMemoryFileSystems;
import com.android.testutils.truth.PathSubject;
import com.android.utils.NullLogger;
import com.android.utils.PathUtils;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.TreeMap;

@RunWith(JUnit4.class)
public final class AvdManagerTest {
    private static final String ANDROID_PREFS_ROOT = "android-home";

    @Rule public final TestName name = new TestName();

    private AndroidSdkHandler mAndroidSdkHandler;
    private AvdManager mAvdManager;
    private Path mAvdFolder;
    private AvdManager mGradleManagedDeviceAvdManager;
    private Path mGradleManagedDeviceAvdFolder;
    private TestSystemImages systemImages;
    private final FileSystem mMockFs = InMemoryFileSystems.createInMemoryFileSystem();

    @Before
    public void setUp() throws Exception {
        Path root = InMemoryFileSystems.getSomeRoot(mMockFs);
        InMemoryFileSystems.recordExistingFile(
                root.resolve("sdk/tools/lib/emulator/snapshots.img"));
        Path prefsRoot = root.resolve(ANDROID_PREFS_ROOT);
        mAndroidSdkHandler = new AndroidSdkHandler(root.resolve("sdk"), prefsRoot);
        systemImages = new TestSystemImages(mAndroidSdkHandler);
        mAvdManager =
                AvdManager.createInstance(
                        mAndroidSdkHandler,
                        prefsRoot.resolve(AbstractAndroidLocations.FOLDER_AVD),
                        DeviceManager.createInstance(mAndroidSdkHandler, NullLogger.getLogger()),
                        NullLogger.getLogger());
        mAvdFolder = AvdInfo.getDefaultAvdFolder(mAvdManager, name.getMethodName(), false);
        mGradleManagedDeviceAvdManager =
                AvdManager.createInstance(
                        mAndroidSdkHandler,
                        prefsRoot
                                .resolve(AbstractAndroidLocations.FOLDER_AVD)
                                .resolve(AbstractAndroidLocations.FOLDER_GRADLE_AVD),
                        DeviceManager.createInstance(mAndroidSdkHandler, NullLogger.getLogger()),
                        NullLogger.getLogger());
        mGradleManagedDeviceAvdFolder =
                AvdInfo.getDefaultAvdFolder(
                        mGradleManagedDeviceAvdManager, name.getMethodName(), false);
    }

    @Test
    public void getPidHardwareQemuIniLockScannerHasNextLong() throws IOException {
        // Arrange
        AvdInfo avd =
                mAvdManager.createAvd(
                        mAvdFolder,
                        name.getMethodName(),
                        systemImages.getApi23().getImage(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        false,
                        false);

        assert avd != null;
        Path file = mAvdManager.resolve(avd, "hardware-qemu.ini.lock");

        Files.createDirectories(file.getParent());
        Files.write(file, "412503".getBytes());

        // Act
        Object pid = mAvdManager.getPid(avd);

        // Assert
        assertEquals(OptionalLong.of(412503), pid);
    }

    @Test
    public void getPidHardwareQemuIniLockIsEmpty() throws IOException {
        // Arrange
        AvdInfo avd =
                mAvdManager.createAvd(
                        mAvdFolder,
                        name.getMethodName(),
                        systemImages.getApi23().getImage(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        false,
                        false);

        assert avd != null;
        Path file = mAvdManager.resolve(avd, "hardware-qemu.ini.lock");

        Files.createDirectories(file.getParent());
        Files.createFile(file);

        // Act
        Object pid = mAvdManager.getPid(avd);

        // Assert
        assertEquals(OptionalLong.empty(), pid);
    }

    @Test
    public void getPidHardwareQemuIniLockScannerDoesntHaveNextLong() throws IOException {
        // Arrange
        AvdInfo avd =
                mAvdManager.createAvd(
                        mAvdFolder,
                        name.getMethodName(),
                        systemImages.getApi23().getImage(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        false,
                        false);

        assert avd != null;
        Path file = mAvdManager.resolve(avd, "hardware-qemu.ini.lock");

        Files.createDirectories(file.getParent());
        Files.write(file, "notlong".getBytes());

        // Act
        Object pid = mAvdManager.getPid(avd);

        // Assert
        assertEquals(OptionalLong.empty(), pid);
    }

    @Test
    public void getPidUserdataQemuImgLockScannerHasNextLong() throws IOException {
        // Arrange
        AvdInfo avd =
                mAvdManager.createAvd(
                        mAvdFolder,
                        name.getMethodName(),
                        systemImages.getApi23().getImage(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        false,
                        false);

        assert avd != null;
        Path file = mAvdManager.resolve(avd, "userdata-qemu.img.lock");

        Files.createDirectories(file.getParent());
        Files.write(file, "412503".getBytes());

        // Act
        Object pid = mAvdManager.getPid(avd);

        // Assert
        assertEquals(OptionalLong.of(412503), pid);
    }

    @Test
    public void getPid() throws IOException {
        // Arrange
        AvdInfo avd =
                mAvdManager.createAvd(
                        mAvdFolder,
                        name.getMethodName(),
                        systemImages.getApi23().getImage(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        false,
                        false);

        assert avd != null;

        // Act
        Object pid = mAvdManager.getPid(avd);

        // Assert
        assertEquals(OptionalLong.empty(), pid);
    }

    @Test
    public void createAvdWithoutSnapshot() {
        MockLog log = new MockLog();
        mAvdManager.createAvd(
                mAvdFolder,
                name.getMethodName(),
                systemImages.getApi23().getImage(),
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                false);

        Path avdConfigFile = mAvdFolder.resolve("config.ini");
        assertTrue("Expected config.ini in " + mAvdFolder, CancellableFileIo.exists(avdConfigFile));
        Map<String, String> properties =
                AvdManager.parseIniFile(new PathFileWrapper(avdConfigFile), null);
        assertFalse(CancellableFileIo.exists(mAvdFolder.resolve("boot.prop")));
        assertEquals(
                "system-images/android-23/default/x86/".replace('/', File.separatorChar),
                properties.get("image.sysdir.1"));
        assertNull(properties.get("snapshot.present"));
        assertFalse(
                "Expected NO " + AvdManager.USERDATA_IMG + " in " + mAvdFolder,
                CancellableFileIo.exists(mAvdFolder.resolve(AvdManager.USERDATA_IMG)));
        assertFalse(
                "Expected NO " + AvdManager.USERDATA_QEMU_IMG + " in " + mAvdFolder,
                CancellableFileIo.exists(mAvdFolder.resolve(AvdManager.USERDATA_QEMU_IMG)));
        assertFalse(
                "Expected NO snapshots.img in " + mAvdFolder,
                CancellableFileIo.exists(mAvdFolder.resolve("snapshots.img")));
    }

    @Test
    public void createAvdWithUserdata() {
        MockLog log = new MockLog();
        mAvdManager.createAvd(
                mAvdFolder,
                name.getMethodName(),
                systemImages.getApi21().getImage(),
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                false);

        Path avdConfigFile = mAvdFolder.resolve("config.ini");
        assertTrue("Expected config.ini in " + mAvdFolder, Files.exists(avdConfigFile));
        Map<String, String> properties =
                AvdManager.parseIniFile(new PathFileWrapper(avdConfigFile), null);
        assertFalse(Files.exists(mAvdFolder.resolve("boot.prop")));
        assertEquals(
                "system-images/android-21/default/x86/".replace('/', File.separatorChar),
                properties.get("image.sysdir.1"));
        assertNull(properties.get("snapshot.present"));
        assertTrue(
                "Expected " + AvdManager.USERDATA_IMG + " in " + mAvdFolder,
                Files.exists(mAvdFolder.resolve(AvdManager.USERDATA_IMG)));
        assertFalse(
                "Expected NO " + AvdManager.USERDATA_QEMU_IMG + " in " + mAvdFolder,
                Files.exists(mAvdFolder.resolve(AvdManager.USERDATA_QEMU_IMG)));
        assertFalse(
                "Expected NO snapshots.img in " + mAvdFolder,
                Files.exists(mAvdFolder.resolve("snapshots.img")));
    }

    @Test
    public void createAvdWithNullValueUserSettings() {
        Map<String, String> userSettings = new HashMap<>();
        userSettings.put(PREFERRED_ABI, null);
        mAvdManager.createAvd(
                mAvdFolder,
                name.getMethodName(),
                systemImages.getApi23().getImage(),
                null,
                null,
                null,
                userSettings,
                null,
                false,
                false,
                false);

        Path avdConfigFile = mAvdFolder.resolve("config.ini");
        assertTrue("Expected config.ini in " + mAvdFolder, CancellableFileIo.exists(avdConfigFile));
        Map<String, String> properties =
                AvdManager.parseIniFile(new PathFileWrapper(avdConfigFile), null);
        assertFalse(CancellableFileIo.exists(mAvdFolder.resolve("boot.prop")));
        assertEquals(
                "system-images/android-23/default/x86/".replace('/', File.separatorChar),
                properties.get("image.sysdir.1"));
        assertNull(properties.get("snapshot.present"));
        assertFalse(
                "Expected NO " + AvdManager.USERDATA_IMG + " in " + mAvdFolder,
                CancellableFileIo.exists(mAvdFolder.resolve(AvdManager.USERDATA_IMG)));
        assertFalse(
                "Expected NO " + AvdManager.USERDATA_QEMU_IMG + " in " + mAvdFolder,
                CancellableFileIo.exists(mAvdFolder.resolve(AvdManager.USERDATA_QEMU_IMG)));
        assertFalse(
                "Expected NO snapshots.img in " + mAvdFolder,
                CancellableFileIo.exists(mAvdFolder.resolve("snapshots.img")));
        Path userSettingsIniFile = AvdInfo.getUserSettingsPath(mAvdFolder);
        assertTrue(
                "Expected user-settings.ini in " + mAvdFolder, Files.exists(userSettingsIniFile));
    }

    @Test
    public void createAvdWithBootProps() {
        MockLog log = new MockLog();
        Map<String, String> expected = Maps.newTreeMap();
        expected.put("ro.build.display.id", "sdk-eng 4.3 JB_MR2 774058 test-keys");
        expected.put("ro.board.platform",   "");
        expected.put("ro.build.tags",       "test-keys");

        mAvdManager.createAvd(
                mAvdFolder,
                name.getMethodName(),
                systemImages.getApi24PlayStore().getImage(),
                null,
                null,
                null,
                expected,
                expected,
                false,
                false,
                false);

        Path bootPropFile = mAvdFolder.resolve("boot.prop");
        assertTrue(Files.exists(bootPropFile));
        Map<String, String> properties =
                AvdManager.parseIniFile(new PathFileWrapper(bootPropFile), null);

        // use a tree map to make sure test order is consistent
        assertEquals(expected.toString(), new TreeMap<>(properties).toString());
    }

    @Test
    public void createChromeOsAvd() {
        MockLog log = new MockLog();

        mAvdManager.createAvd(
                mAvdFolder,
                name.getMethodName(),
                systemImages.getChromeOs().getImage(),
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                false);

        Path avdConfigFile = mAvdFolder.resolve("config.ini");
        assertTrue("Expected config.ini in " + mAvdFolder, Files.exists(avdConfigFile));
        Map<String, String> properties =
                AvdManager.parseIniFile(new PathFileWrapper(avdConfigFile), null);
        assertEquals("true", properties.get("hw.arc"));
        assertEquals("x86_64", properties.get("hw.cpu.arch"));
    }

    @Test
    public void createNonChromeOsAvd() {
        MockLog log = new MockLog();

        mAvdManager.createAvd(
                mAvdFolder,
                name.getMethodName(),
                systemImages.getApi23().getImage(),
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                false);

        Path avdConfigFile = mAvdFolder.resolve("config.ini");
        assertTrue("Expected config.ini in " + mAvdFolder, Files.exists(avdConfigFile));
        Map<String, String> properties =
                AvdManager.parseIniFile(new PathFileWrapper(avdConfigFile), null);
        assertEquals("false", properties.get("hw.arc"));
        assertEquals("x86", properties.get("hw.cpu.arch"));
    }

    @Test
    public void createAvdForGradleManagedDevice() throws AndroidLocationsException {
        MockLog log = new MockLog();
        mGradleManagedDeviceAvdManager.createAvd(
                mGradleManagedDeviceAvdFolder,
                name.getMethodName(),
                systemImages.getApi23().getImage(),
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                false);

        assertThat(mGradleManagedDeviceAvdManager.getAllAvds()).hasLength(1);

        // Creating AVD in Gradle Managed Device folder (.android/avd/gradle-managed) should not
        // confuse the standard AVD manager (.android/avd).
        mAvdManager.reloadAvds();
        assertThat(mAvdManager.getAllAvds()).isEmpty();
    }

    @Test
    public void createTabletAvd() {
        mAvdManager.createAvd(
                mAvdFolder,
                name.getMethodName(),
                systemImages.getApi34TabletPlayStore().getImage(),
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                false);

        Path avdConfigFile = mAvdFolder.resolve("config.ini");
        assertTrue("Expected config.ini in " + mAvdFolder, Files.exists(avdConfigFile));
        Map<String, String> properties =
                AvdManager.parseIniFile(new PathFileWrapper(avdConfigFile), null);
        assertEquals("google_apis_playstore", properties.get("tag.id"));
        assertEquals("Google Play", properties.get("tag.display"));
        assertEquals("google_apis_playstore,tablet", properties.get("tag.ids"));
        assertEquals("Google Play,Tablet", properties.get("tag.displaynames"));
    }

    @Test
    public void createAvdWithSkin() {
        MockLog log = new MockLog();
        DeviceManager deviceManager = DeviceManager.createInstance(mAndroidSdkHandler, log);
        Device device = deviceManager.getDevice("medium_phone", "Generic");
        AvdBuilder builder = mAvdManager.createAvdBuilder(device);

        Path skinPath = mAndroidSdkHandler.getLocation().resolve("skins").resolve("pixel_8");
        InMemoryFileSystems.recordExistingFile(skinPath.resolve("layout"));

        builder.setAvdName(name.getMethodName());
        builder.setAvdFolder(mAvdFolder);
        builder.setSystemImage(systemImages.getApi23().getImage());
        builder.setSkin(new OnDiskSkin(skinPath));

        mAvdManager.createAvd(builder);

        Map<String, String> config = AvdManager.parseIniFile(
                new PathFileWrapper(mAvdFolder.resolve("config.ini")), null);
        assertThat(config.get(ConfigKey.SKIN_NAME)).isEqualTo(skinPath.getFileName().toString());
        assertThat(config.get(ConfigKey.SKIN_PATH)).isEqualTo(skinPath.toString());
    }

    @Test
    public void moveAvd() {
        Map<String, String> hardwareConfig =
                ImmutableMap.of("ro.build.display.id", "sdk-eng 4.3 JB_MR2 774058 test-keys");
        Map<String, String> userSettings = ImmutableMap.of("abi.type.preferred", "x86");
        Map<String, String> bootProps = ImmutableMap.of("ro.emulator.circular", "true");

        AvdInfo avdInfo =
                mAvdManager.createAvd(
                        mAvdFolder,
                        name.getMethodName(),
                        systemImages.getApi24PlayStore().getImage(),
                        null,
                        new InternalSdCard(SDCARD_MIN_BYTE_SIZE),
                        hardwareConfig,
                        userSettings,
                        bootProps,
                        true,
                        false,
                        false);

        Path metadataIniFile = mAvdFolder.getParent().resolve(name.getMethodName() + ".ini");

        assertTrue(Files.exists(metadataIniFile));
        assertTrue(Files.exists(mAvdFolder.resolve("boot.prop")));
        assertTrue(Files.exists(mAvdFolder.resolve("user-settings.ini")));

        // Move the AVD, updating its name and data folder path
        String newAvdName = avdInfo.getName() + "_2";
        Path newAvdFolder = avdInfo.getDataFolderPath().resolveSibling(newAvdName + ".avd");
        assertThat(mAvdManager.moveAvd(avdInfo, newAvdName, newAvdFolder)).isTrue();

        // The locations of the metadata .ini and the data folder are updated
        assertFalse(Files.exists(metadataIniFile));
        assertFalse(Files.isDirectory(mAvdFolder));
        Path newMetadataIniPath = metadataIniFile.resolveSibling(newAvdName + ".ini");
        assertTrue(Files.exists(newMetadataIniPath));
        assertTrue(Files.isDirectory(newAvdFolder));

        // The contents of the metadata .ini reflect the new paths
        Map <String, String> metadata =
                AvdManager.parseIniFile(new PathFileWrapper(newMetadataIniPath), null);
        assertThat(metadata.get(MetadataKey.ABS_PATH)).isEqualTo(newAvdFolder.toString());
        assertThat(metadata.get(MetadataKey.REL_PATH))
                .isEqualTo(newAvdFolder.getParent().getParent().relativize(newAvdFolder).toString());

        Map<String, String> movedBootProps =
                AvdManager.parseIniFile(
                        new PathFileWrapper(newAvdFolder.resolve("boot.prop")), null);
        movedBootProps.remove(ENCODING);
        assertThat(movedBootProps).isEqualTo(bootProps);

        Map<String, String> movedUserSettings =
                AvdManager.parseIniFile(
                        new PathFileWrapper(newAvdFolder.resolve("user-settings.ini")), null);
        movedUserSettings.remove(ENCODING);
        assertThat(movedUserSettings).isEqualTo(userSettings);

        Map<String, String> movedConfig =
                AvdManager.parseIniFile(
                        new PathFileWrapper(newAvdFolder.resolve("config.ini")), null);
        movedConfig.remove(ENCODING);
        assertThat(movedConfig)
                .containsEntry("ro.build.display.id", "sdk-eng 4.3 JB_MR2 774058 test-keys");
    }

    @Test
    public void renameAvd() {
        MockLog log = new MockLog();
        // Create an AVD
        AvdInfo origAvd =
                mAvdManager.createAvd(
                        mAvdFolder,
                        name.getMethodName(),
                        systemImages.getApi23().getImage(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        false,
                        false);

        assertNotNull("Could not create AVD", origAvd);
        Path avdConfigFile = mAvdFolder.resolve("config.ini");
        assertTrue("Expected config.ini in " + mAvdFolder, Files.exists(avdConfigFile));
        assertFalse(Files.exists(mAvdFolder.resolve("boot.prop")));
        Map<String, String> properties =
                AvdManager.parseIniFile(new PathFileWrapper(avdConfigFile), null);
        assertEquals(
                "system-images/android-23/default/x86/".replace('/', File.separatorChar),
                properties.get("image.sysdir.1"));
        assertFalse(
                "Expected NO " + AvdManager.USERDATA_IMG + " in " + mAvdFolder,
                Files.exists(mAvdFolder.resolve(AvdManager.USERDATA_IMG)));
        assertFalse(
                "Expected NO " + AvdManager.USERDATA_QEMU_IMG + " in " + mAvdFolder,
                Files.exists(mAvdFolder.resolve(AvdManager.USERDATA_QEMU_IMG)));

        // Create an AVD that is the same, but with a different name
        String newName = name.getMethodName() + "_renamed";
        AvdInfo renamedAvd =
                mAvdManager.createAvd(
                        mAvdFolder,
                        newName,
                        systemImages.getApi23().getImage(),
                        null,
                        null,
                        null,
                        properties,
                        null,
                        false,
                        false,
                        true /* Yes, edit the existing AVD*/);

        assertNotNull("Could not rename AVD", renamedAvd);
        Path parentFolder = mAvdFolder.getParent();
        String newNameIni = newName + ".ini";
        Path newAvdConfigFile = parentFolder.resolve(newNameIni);
        assertTrue(
                "Expected renamed " + newNameIni + " in " + parentFolder,
                Files.exists(newAvdConfigFile));
        Map<String, String> newProperties =
                AvdManager.parseIniFile(new PathFileWrapper(newAvdConfigFile), null);
        assertEquals(mAvdFolder.toString(), newProperties.get("path"));

        assertFalse(Files.exists(mAvdFolder.resolve("boot.prop")));
        avdConfigFile = mAvdFolder.resolve("config.ini");
        Map<String, String> baseProperties =
                AvdManager.parseIniFile(new PathFileWrapper(avdConfigFile), null);
        assertEquals(
                "system-images/android-23/default/x86/".replace('/', File.separatorChar),
                baseProperties.get("image.sysdir.1"));
        assertFalse(
                "Expected NO " + AvdManager.USERDATA_IMG + " in " + mAvdFolder,
                Files.exists(mAvdFolder.resolve(AvdManager.USERDATA_IMG)));
        assertFalse(
                "Expected NO " + AvdManager.USERDATA_QEMU_IMG + " in " + mAvdFolder,
                Files.exists(mAvdFolder.resolve(AvdManager.USERDATA_QEMU_IMG)));
    }

    @Test
    public void editAvdViaBuilder() {
        MockLog log = new MockLog();
        DeviceManager deviceManager = DeviceManager.createInstance(mAndroidSdkHandler, log);
        Device device = deviceManager.getDevice("medium_phone", "Generic");

        AvdBuilder builder = mAvdManager.createAvdBuilder(device);
        builder.setSystemImage(systemImages.getApi33ext4().getImage());
        AvdInfo initialAvdInfo = mAvdManager.createAvd(builder);

        assertThat(initialAvdInfo.getName()).isEqualTo("Medium_Phone");
        assertThat(initialAvdInfo).isNotNull();

        Path initialMetadataPath = builder.getMetadataIniPath();
        assertThat(Files.exists(initialMetadataPath)).isTrue();

        builder.setAvdName("My_Phone");
        builder.setBootMode(ColdBoot.INSTANCE);

        AvdInfo editedAvdInfo = mAvdManager.editAvd(initialAvdInfo, builder);

        assertThat(editedAvdInfo.getName()).isEqualTo("My_Phone");
        assertThat(editedAvdInfo.getProperties())
                .containsEntry(ConfigKey.FORCE_COLD_BOOT_MODE, "yes");
        assertThat((Object) builder.getMetadataIniPath()).isNotEqualTo(initialMetadataPath);
        assertThat(Files.exists(builder.getMetadataIniPath())).isTrue();
    }

    @Test
    public void duplicateAvd() throws Exception {
        MockLog log = new MockLog();
        // Create an AVD
        HashMap<String, String> origAvdConfig = new HashMap<>();
        origAvdConfig.put("testKey1", "originalValue1");
        origAvdConfig.put("testKey2", "originalValue2");
        AvdInfo origAvd =
                mAvdManager.createAvd(
                        mAvdFolder,
                        name.getMethodName(),
                        systemImages.getApi24PlayStore().getImage(),
                        null,
                        new InternalSdCard(100 << 20), // SD card size
                        origAvdConfig,
                        origAvdConfig,
                        null,
                        false,
                        false,
                        false);

        assertNotNull("Could not create AVD", origAvd);
        // Put some extra files in the AVD directory
        Files.createFile(mAvdFolder.resolve("foo.bar"));
        InMemoryFileSystems.recordExistingFile(
                mAvdFolder.resolve("hardware-qemu.ini"),
                "avd.name="
                        + name.getMethodName()
                        + "\nhw.sdCard.path="
                        + mAvdFolder.toAbsolutePath()
                        + "/sdcard.img");

        // Copy this AVDa to an AVD with a different name and a slightly different configuration
        HashMap<String, String> newAvdConfig = new HashMap<>();
        newAvdConfig.put("testKey2", "newValue2");

        String newName = "Copy_of_" + name.getMethodName();
        AvdInfo duplicatedAvd =
                mAvdManager.createAvd(
                        mAvdFolder,
                        newName,
                        systemImages.getApi24PlayStore().getImage(),
                        null,
                        new InternalSdCard(222 << 20), // Different SD card size
                        newAvdConfig,
                        null,
                        null,
                        false,
                        false,
                        false); // Do not remove the original

        // Verify that the duplicated AVD is correct
        assertNotNull("Could not duplicate AVD", duplicatedAvd);
        Path parentFolder = mAvdFolder.getParent();
        Path newFolder = parentFolder.resolve(newName + ".avd");
        String newNameIni = newName + ".ini";
        Path newIniFile = parentFolder.resolve(newNameIni);
        assertTrue("Expected " + newNameIni + " in " + parentFolder, Files.exists(newIniFile));
        Map<String, String> iniProperties =
                AvdManager.parseIniFile(new PathFileWrapper(newIniFile), null);
        assertEquals(newFolder.toString(), iniProperties.get("path"));

        assertTrue(Files.exists(newFolder.resolve("foo.bar")));
        assertFalse(Files.exists(newFolder.resolve("boot.prop")));
        // Check the config.ini file
        Map<String, String> configProperties =
                AvdManager.parseIniFile(new PathFileWrapper(newFolder.resolve("config.ini")), null);
        assertEquals(
                "system-images/android-24/google_apis_playstore/x86_64/"
                        .replace('/', File.separatorChar),
                configProperties.get("image.sysdir.1"));
        assertEquals(newName, configProperties.get("AvdId"));
        assertEquals(newName, configProperties.get("avd.ini.displayname"));
        assertEquals("222M", configProperties.get("sdcard.size"));
        assertEquals("originalValue1", configProperties.get("testKey1"));
        assertEquals("newValue2", configProperties.get("testKey2"));
        assertFalse(
                "Expected NO " + AvdManager.USERDATA_IMG + " in " + newFolder,
                Files.exists(newFolder.resolve(AvdManager.USERDATA_IMG)));
        assertFalse(
                "Expected NO " + AvdManager.USERDATA_QEMU_IMG + " in " + mAvdFolder,
                Files.exists(mAvdFolder.resolve(AvdManager.USERDATA_QEMU_IMG)));

        // Check the hardware-qemu.ini file
        Map<String, String> hardwareProperties =
                AvdManager.parseIniFile(
                        new PathFileWrapper(newFolder.resolve("hardware-qemu.ini")), null);
        assertEquals(newName, hardwareProperties.get("avd.name"));
        assertEquals(
                mAvdFolder.getParent().toAbsolutePath()
                        + File.separator
                        + newName
                        + ".avd/sdcard.img",
                hardwareProperties.get("hw.sdCard.path"));

        // Quick check that the original AVD directory still exists
        assertTrue(Files.exists(mAvdFolder.resolve("foo.bar")));
        assertTrue(Files.exists(mAvdFolder.resolve("config.ini")));
        assertTrue(Files.exists(mAvdFolder.resolve("hardware-qemu.ini")));
        Map<String, String> baseConfigProperties =
                AvdManager.parseIniFile(
                        new PathFileWrapper(mAvdFolder.resolve("config.ini")), null);
        assertThat(baseConfigProperties.get("AvdId")).isNotEqualTo(newName); // Different or null
    }

    @Test
    public void reloadAvds() throws Exception {
        // Create an AVD.
        AvdInfo avd =
                mAvdManager.createAvd(
                        mAvdFolder,
                        name.getMethodName(),
                        systemImages.getApi23().getImage(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        false,
                        false);
        assertNotNull("Could not create AVD", avd);
        assertEquals(AvdInfo.AvdStatus.OK, avd.getStatus());

        // Delete the system image of the AVD.
        PathUtils.deleteRecursivelyIfExists(systemImages.getApi23().getImage().getLocation());
        mAvdManager.reloadAvds();
        avd = mAvdManager.getAvd(avd.getName(), false);
        assertNotNull(avd);
        assertEquals(AvdInfo.AvdStatus.ERROR_IMAGE_MISSING, avd.getStatus());
    }

    @Test
    public void playStoreProperty() {
        MockLog log = new MockLog();
        Map<String, String> expected = Maps.newTreeMap();
        expected.put("ro.build.display.id", "sdk-eng 4.3 JB_MR2 774058 test-keys");
        expected.put("ro.board.platform",   "");
        expected.put("ro.build.tags",       "test-keys");

        // Play Store image with Play Store device
        mAvdManager.createAvd(
                mAvdFolder,
                name.getMethodName(),
                systemImages.getApi24PlayStore().getImage(),
                null,
                null,
                null,
                expected,
                expected,
                true, // deviceHasPlayStore
                false,
                false);

        Path configIniFile = mAvdFolder.resolve("config.ini");
        Map<String, String> baseProperties =
                AvdManager.parseIniFile(new PathFileWrapper(configIniFile), null);
        assertEquals("true", baseProperties.get("PlayStore.enabled"));

        // Play Store image with non-Play Store device
        mAvdManager.createAvd(
                mAvdFolder,
                name.getMethodName(),
                systemImages.getApi24PlayStore().getImage(),
                null,
                null,
                null,
                null,
                expected,
                false, // deviceHasPlayStore
                true,
                false);
        baseProperties = AvdManager.parseIniFile(new PathFileWrapper(configIniFile), null);
        assertEquals("false", baseProperties.get("PlayStore.enabled"));

        // Non-Play Store image with Play Store device
        mAvdManager.createAvd(
                mAvdFolder,
                name.getMethodName(),
                systemImages.getApi23GoogleApis().getImage(),
                null,
                null,
                null,
                null,
                expected,
                true, // deviceHasPlayStore
                true,
                false);
        baseProperties = AvdManager.parseIniFile(new PathFileWrapper(configIniFile), null);
        assertEquals("false", baseProperties.get("PlayStore.enabled"));

        // Wear image API 24 (no Play Store)
        mAvdManager.createAvd(
                mAvdFolder,
                name.getMethodName(),
                systemImages.getApi24Wear().getImage(),
                null,
                null,
                null,
                null,
                expected,
                true, // deviceHasPlayStore
                true,
                false);
        baseProperties = AvdManager.parseIniFile(new PathFileWrapper(configIniFile), null);
        assertEquals("false", baseProperties.get("PlayStore.enabled"));

        // Wear image API 25 (with Play Store)
        // (All Wear devices have Play Store)
        mAvdManager.createAvd(
                mAvdFolder,
                name.getMethodName(),
                systemImages.getApi25Wear().getImage(),
                null,
                null,
                null,
                null,
                expected,
                true, // deviceHasPlayStore
                true,
                false);
        baseProperties = AvdManager.parseIniFile(new PathFileWrapper(configIniFile), null);
        assertEquals("true", baseProperties.get("PlayStore.enabled"));

        // Wear image for China (no Play Store)
        mAvdManager.createAvd(
                mAvdFolder,
                name.getMethodName(),
                systemImages.getApi25WearChina().getImage(),
                null,
                null,
                null,
                null,
                expected,
                true, // deviceHasPlayStore
                true,
                false);
        baseProperties = AvdManager.parseIniFile(new PathFileWrapper(configIniFile), null);
        assertEquals("false", baseProperties.get("PlayStore.enabled"));
    }

    @Test
    public void updateDeviceChanged() throws Exception {
        MockLog log = new MockLog();

        DeviceManager devMan = DeviceManager.createInstance(mAndroidSdkHandler, log);
        Device myDevice = devMan.getDevice("7.6in Foldable", "Generic");
        Map<String, String> baseHardwareProperties = DeviceManager.getHardwareProperties(myDevice);

        // Modify hardware properties that should change
        baseHardwareProperties.put("hw.lcd.height", "960");
        baseHardwareProperties.put("hw.displayRegion.0.1.height", "480");
        // Modify a hardware property that should NOT change
        baseHardwareProperties.put("hw.ramSize", "1536");
        // Add a user-settable property
        baseHardwareProperties.put("hw.keyboard", "yes");

        // Create a virtual device including these properties
        AvdInfo myDeviceInfo =
                mAvdManager.createAvd(
                        mAvdFolder,
                        name.getMethodName(),
                        systemImages.getApi23GoogleApis().getImage(),
                        null,
                        null,
                        baseHardwareProperties,
                        baseHardwareProperties,
                        null,
                        true,
                        true,
                        false);

        // Verify all the parameters that we changed and the parameter that we added
        Map<String, String> firstHardwareProperties = myDeviceInfo.getProperties();
        assertEquals("960",  firstHardwareProperties.get("hw.lcd.height"));
        assertEquals("480",  firstHardwareProperties.get("hw.displayRegion.0.1.height"));
        assertEquals("1536", firstHardwareProperties.get("hw.ramSize"));
        assertEquals("yes",  firstHardwareProperties.get("hw.keyboard"));

        // Update the device using the original hardware definition
        AvdInfo updatedDeviceInfo = mAvdManager.updateDeviceChanged(myDeviceInfo);

        // Verify that the two fixed hardware properties changed back, but the other hardware
        // property and the user-settable property did not change.
        Map<String, String> updatedHardwareProperties = updatedDeviceInfo.getProperties();
        assertEquals("2208", updatedHardwareProperties.get("hw.lcd.height"));
        assertEquals("2208",  updatedHardwareProperties.get("hw.displayRegion.0.1.height"));
        assertEquals("1536", updatedHardwareProperties.get("hw.ramSize"));
        assertEquals("yes",  updatedHardwareProperties.get("hw.keyboard"));
    }

    @Test
    public void parseAvdInfo() throws Exception {
        MockLog log = new MockLog();
        mAvdManager.createAvd(
                mAvdFolder,
                name.getMethodName(),
                systemImages.getApi23().getImage(),
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                false);

        // Check a valid AVD .ini file
        Path parentFolder = mAvdFolder.getParent();
        String avdIniName = name.getMethodName() + ".ini";
        Path avdIniFile = parentFolder.resolve(avdIniName).toAbsolutePath();
        assertTrue("Expected AVD .ini in " + parentFolder, Files.exists(avdIniFile));
        AvdInfo avdInfo = mAvdManager.parseAvdInfo(avdIniFile);
        assertThat(avdInfo.getStatus()).isEqualTo(AvdInfo.AvdStatus.OK);
        PathSubject.assertThat(avdInfo.getDataFolderPath()).isEqualTo(mAvdFolder);
        assertThat(avdInfo.getAndroidVersion()).isEqualTo(new AndroidVersion(23));

        // Check a bad AVD .ini file.
        // Append garbage to make the file invalid.
        try (OutputStream corruptedStream =
                        Files.newOutputStream(avdIniFile, StandardOpenOption.APPEND);
                BufferedWriter corruptedWriter =
                        new BufferedWriter(new OutputStreamWriter(corruptedStream))) {
            corruptedWriter.write("[invalid syntax]\n");
        }
        AvdInfo corruptedInfo = mAvdManager.parseAvdInfo(avdIniFile);
        assertThat(corruptedInfo.getStatus()).isEqualTo(AvdInfo.AvdStatus.ERROR_CORRUPTED_INI);

        // Check a non-existent AVD .ini file
        String noSuchIniName = "noSuch.ini";
        Path noSuchIniFile = parentFolder.resolve(noSuchIniName);
        assertFalse("Found unexpected noSuch.ini in " + parentFolder, Files.exists(noSuchIniFile));
        AvdInfo noSuchInfo = mAvdManager.parseAvdInfo(noSuchIniFile);
        assertThat(noSuchInfo.getStatus()).isEqualTo(AvdInfo.AvdStatus.ERROR_CORRUPTED_INI);

        // Check an empty AVD .ini file
        Path emptyIniFile = parentFolder.resolve("empty.ini");
        assertNotNull(
                "Empty .ini file already exists in " + parentFolder,
                Files.createFile(emptyIniFile));
        assertTrue("Expected empty AVD .ini in " + parentFolder, Files.exists(emptyIniFile));
        assertThat(Files.size(emptyIniFile)).isEqualTo(0);
        AvdInfo emptyInfo = mAvdManager.parseAvdInfo(emptyIniFile);
        assertThat(emptyInfo.getStatus()).isEqualTo(AvdInfo.AvdStatus.ERROR_CORRUPTED_INI);
    }

    @Test
    public void parseAvdInfoWithExtensionLevel() throws Exception {
        mAvdManager.createAvd(
                mAvdFolder,
                name.getMethodName(),
                systemImages.getApi33ext4().getImage(),
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                false);

        // Check a valid AVD .ini file
        Path parentFolder = mAvdFolder.getParent();
        String avdIniName = name.getMethodName() + ".ini";
        Path avdIniFile = parentFolder.resolve(avdIniName).toAbsolutePath();
        assertTrue("Expected AVD .ini in " + parentFolder, Files.exists(avdIniFile));
        AvdInfo avdInfo = mAvdManager.parseAvdInfo(avdIniFile);
        assertThat(avdInfo.getStatus()).isEqualTo(AvdInfo.AvdStatus.OK);
        PathSubject.assertThat(avdInfo.getDataFolderPath()).isEqualTo(mAvdFolder);

        // check that the properties of the AVD contain the extension
        String extension = avdInfo.getProperty(ConfigKey.ANDROID_EXTENSION);
        String isBaseExtension = avdInfo.getProperty(ConfigKey.ANDROID_IS_BASE_EXTENSION);

        assertThat(extension).isEqualTo("4");
        assertThat(isBaseExtension).isEqualTo("false");
    }
}
