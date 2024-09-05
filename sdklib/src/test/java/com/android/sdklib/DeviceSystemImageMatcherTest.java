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
package com.android.sdklib;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.Hardware;
import com.android.sdklib.devices.Screen;
import com.android.sdklib.devices.Software;
import com.android.sdklib.repository.IdDisplay;

import com.google.common.collect.ImmutableList;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(JUnit4.class)
public final class DeviceSystemImageMatcherTest {

    @Test
    public void matchesDeviceIsntTabletAndImageTagsContainsTabletTag() {
        // Arrange
        Device device = mockDevice(null);

        ISystemImage image =
                mockImage(
                        Arrays.asList(SystemImageTags.GOOGLE_APIS_TAG, SystemImageTags.TABLET_TAG));

        // Act
        boolean matches = DeviceSystemImageMatcher.matches(device, image);

        // Assert
        assertFalse(matches);
    }

    @Test
    public void matchesDeviceTagIdIsNullAndImageIsWearOsImage() {
        // Arrange
        Device device = mockDevice(null);

        ISystemImage image =
                mockImage(
                        Collections.singletonList(
                                IdDisplay.create("android-wear", "Android Wear")));

        // Act
        boolean matches = DeviceSystemImageMatcher.matches(device, image);

        // Assert
        assertFalse(matches);
    }

    @Test
    public void matchesDeviceTagIdEqualsDefaultTagIdAndImageIsWearOsImage() {
        Device device = mockDevice(SystemImageTags.DEFAULT_TAG.getId());

        ISystemImage image =
                mockImage(
                        Collections.singletonList(
                                IdDisplay.create("android-wear", "Android Wear")));

        // Act
        boolean matches = DeviceSystemImageMatcher.matches(device, image);

        // Assert
        assertFalse(matches);
    }

    @Test
    public void matchesDeviceTagIdIsNullAndImageIsDesktopImage() {
        // Arrange
        Device device = mockDevice(null);
        ISystemImage image = mockImage(Collections.singletonList(SystemImageTags.DESKTOP_TAG));

        // Act
        boolean matches = DeviceSystemImageMatcher.matches(device, image);

        // Assert
        assertFalse(matches);
    }

    @Test
    public void matchesDeviceTagIdEqualsDefaultTagIdAndImageIsDesktopImage() {
        // Arrange
        Device device = mockDevice(SystemImageTags.DEFAULT_TAG.getId());
        ISystemImage image = mockImage(Collections.singletonList(SystemImageTags.DESKTOP_TAG));

        // Act
        boolean matches = DeviceSystemImageMatcher.matches(device, image);

        // Assert
        assertFalse(matches);
    }

    @Test
    public void matchesDeviceTagIdIsNullAndImageIsTvImage() {
        // Arrange
        Device device = mockDevice(null);
        ISystemImage image = mockImage(Collections.singletonList(SystemImageTags.ANDROID_TV_TAG));

        // Act
        boolean matches = DeviceSystemImageMatcher.matches(device, image);

        // Assert
        assertFalse(matches);
    }

    @Test
    public void matchesDeviceTagIdEqualsDefaultTagIdAndImageIsTvImage() {
        // Arrange
        Device device = mockDevice(SystemImageTags.DEFAULT_TAG.getId());
        ISystemImage image = mockImage(Collections.singletonList(SystemImageTags.ANDROID_TV_TAG));

        // Act
        boolean matches = DeviceSystemImageMatcher.matches(device, image);

        // Assert
        assertFalse(matches);
    }

    @Test
    public void matchesDeviceTagIdIsNullAndImageIsAutomotiveImage() {
        // Arrange
        Device device = mockDevice(null);

        ISystemImage image =
                mockImage(
                        Collections.singletonList(
                                IdDisplay.create("android-automotive-playstore", "Automotive")));

        // Act
        boolean matches = DeviceSystemImageMatcher.matches(device, image);

        // Assert
        assertFalse(matches);
    }

    @Test
    public void matchesDeviceTagIdEqualsDefaultTagIdAndImageIsAutomotiveImage() {
        // Arrange
        Device device = mockDevice(SystemImageTags.DEFAULT_TAG.getId());

        ISystemImage image =
                mockImage(
                        Collections.singletonList(
                                IdDisplay.create("android-automotive-playstore", "Automotive")));

        // Act
        boolean matches = DeviceSystemImageMatcher.matches(device, image);

        // Assert
        assertFalse(matches);
    }

    @Test
    public void matchesDeviceTagIdIsNull() {
        // Arrange
        Device device = mockDevice(null);
        ISystemImage image = mockImage(Collections.emptyList());

        // Act
        boolean matches = DeviceSystemImageMatcher.matches(device, image);

        // Assert
        assertTrue(matches);
    }

    @Test
    public void matchesDeviceTagIdEqualsDefaultTagId() {
        // Arrange
        Device device = mockDevice(SystemImageTags.DEFAULT_TAG.getId());
        ISystemImage image = mockImage(Collections.emptyList());

        // Act
        boolean matches = DeviceSystemImageMatcher.matches(device, image);

        // Assert
        assertTrue(matches);
    }

    @Test
    public void matchesDeviceTagIdEqualsAndroidTvTagIdAndImageIsTvImage() {
        // Arrange
        Device device = mockDevice(SystemImageTags.ANDROID_TV_TAG.getId());
        ISystemImage image = mockImage(Collections.singletonList(SystemImageTags.ANDROID_TV_TAG));

        // Act
        boolean matches = DeviceSystemImageMatcher.matches(device, image);

        // Assert
        assertTrue(matches);
    }

    @Test
    public void matchesDeviceTagIdEqualsAndroidTvTagIdAndImageIsntTvImage() {
        // Arrange
        Device device = mockDevice(SystemImageTags.ANDROID_TV_TAG.getId());
        ISystemImage image = mockImage(Collections.singletonList(SystemImageTags.GOOGLE_APIS_TAG));

        // Act
        boolean matches = DeviceSystemImageMatcher.matches(device, image);

        // Assert
        assertFalse(matches);
    }

    @Test
    public void matchesDeviceTagIdEqualsGoogleTvTagIdAndImageIsTvImage() {
        // Arrange
        Device device = mockDevice(SystemImageTags.GOOGLE_TV_TAG.getId());
        ISystemImage image = mockImage(Collections.singletonList(SystemImageTags.ANDROID_TV_TAG));

        // Act
        boolean matches = DeviceSystemImageMatcher.matches(device, image);

        // Assert
        assertTrue(matches);
    }

    @Test
    public void matchesDeviceTagIdEqualsGoogleTvTagIdAndImageIsntTvImage() {
        // Arrange
        Device device = mockDevice(SystemImageTags.GOOGLE_TV_TAG.getId());
        ISystemImage image = mockImage(Collections.singletonList(SystemImageTags.GOOGLE_APIS_TAG));

        // Act
        boolean matches = DeviceSystemImageMatcher.matches(device, image);

        // Assert
        assertFalse(matches);
    }

    @Test
    public void matchesNoImageTagIdMatchesDeviceTagId() {
        // Arrange
        Device device = mockDevice("android-wear");
        ISystemImage image = mockImage(Collections.singletonList(SystemImageTags.GOOGLE_APIS_TAG));

        // Act
        boolean matches = DeviceSystemImageMatcher.matches(device, image);

        // Assert
        assertFalse(matches);
    }

    @Test
    public void matchesApiLevelIncompatible() {
        Device device = mockDevice("default", 34, Integer.MAX_VALUE);
        ISystemImage image = mockImage(Collections.emptyList(), 33, false);

        assertFalse(DeviceSystemImageMatcher.matches(device, image));
    }

    @Test
    public void matchesPlayImageRequiresPlayDevice() {
        Device device = mockDevice("default");
        ISystemImage image = mockImage(Collections.emptyList(), 33, true);

        assertFalse(DeviceSystemImageMatcher.matches(device, image));
    }

    @Test
    public void matches() {
        // Arrange
        Device device = mockDevice("android-wear");

        ISystemImage image =
                mockImage(
                        Collections.singletonList(
                                IdDisplay.create("android-wear", "Android Wear")));

        // Act
        boolean matches = DeviceSystemImageMatcher.matches(device, image);

        // Assert
        assertTrue(matches);
    }

    @NonNull
    private static Device mockDevice(@Nullable String tagId) {
        return mockDevice(tagId, 30, 34);
    }

    private static Device mockDevice(@Nullable String tagId, int minApiLevel, int maxApiLevel) {
        Screen screen = Mockito.mock(Screen.class);

        Hardware hardware = Mockito.mock(Hardware.class);
        Mockito.when(hardware.getScreen()).thenReturn(screen);

        Device device = Mockito.mock(Device.class);
        Mockito.when(device.getTagId()).thenReturn(tagId);
        Mockito.when(device.getDefaultHardware()).thenReturn(hardware);

        Software software = Mockito.mock(Software.class);
        Mockito.when(software.getMinSdkLevel()).thenReturn(minApiLevel);
        Mockito.when(software.getMaxSdkLevel()).thenReturn(maxApiLevel);
        Mockito.when(device.getSoftware(0)).thenReturn(software);
        Mockito.when(device.getAllSoftware()).thenReturn(ImmutableList.of(software));

        return device;
    }

    @NonNull
    private static ISystemImage mockImage(@NonNull List<IdDisplay> tags) {
        return mockImage(tags, 33, false);
    }

    @NonNull
    private static ISystemImage mockImage(
            @NonNull List<IdDisplay> tags, int apiLevel, boolean hasPlayStore) {
        ISystemImage image = Mockito.mock(ISystemImage.class);
        Mockito.when(image.getTags()).thenReturn(tags);
        Mockito.when(image.getAndroidVersion()).thenReturn(new AndroidVersion(apiLevel));

        Mockito.when(image.hasPlayStore()).thenReturn(hasPlayStore);

        return image;
    }
}
