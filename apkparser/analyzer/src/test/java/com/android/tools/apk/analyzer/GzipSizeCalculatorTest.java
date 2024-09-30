/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.apk.analyzer;

import static com.google.common.truth.Truth.assertThat;

import com.android.testutils.TestResources;
import com.android.tools.apk.analyzer.internal.GzipSizeCalculator;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public class GzipSizeCalculatorTest {

    private static final String VIRTUAL_ENTRY_NAME = "";

    private ApkSizeCalculator calculator;
    private Path apk;

    @Before
    public void setup() {
        apk = TestResources.getFile("/test.apk").toPath();
        calculator = new GzipSizeCalculator();
    }

    @Test
    public void getFullApkDownloadSize() {
        long expected = 502; // gzip -9 test.apk; ls -l test.apk.gz
        assertThat(calculator.getFullApkDownloadSize(apk)).isEqualTo(expected);
    }

    @Test
    public void getFullApkRawSize() {
        long expected = 960; // ls -l test.apk
        assertThat(calculator.getFullApkRawSize(apk)).isEqualTo(expected);
    }

    @Test
    public void getDownloadSizePerFile() {
        Map<String, Long> downloadSizePerFile = calculator.getDownloadSizePerFile(apk);

        // The expected values can be seen via:
        //     unzip -cq test.apk AndroidManifest.xml | gzip --best | wc -c
        // Note: for this test apk, the re-compressing AndroidManifest.xml results in a larger file
        // due to Gzip overhead (header etc.).
        assertThat(downloadSizePerFile.get("/AndroidManifest.xml")).isEqualTo(29);
        assertThat(downloadSizePerFile.get("/res/"))
                .isNull(); // directories should not have any size
    }

    @Test
    public void getInfoPerFile() {
        Map<String, ZipEntryInfo> infoPerFile = calculator.getInfoPerFile(apk);

        // The expected values can be seen via unzip -lv resources/test.apk
        assertThat(infoPerFile.get("/AndroidManifest.xml").size).isEqualTo(11);
        assertThat(infoPerFile.get("/res/")).isNull(); // directories should not have any info
    }

    @Test
    public void getDownloadSizePerFile_filtersVirtualEntry() throws IOException {
        Path apkWithVirtualEntry = TestResources.getFile("/app_with_virtual_entry.apk").toPath();

        // Make sure the archive has one virtual entry.
        VirtualEntryCalculator veCalculator = new VirtualEntryCalculator(apkWithVirtualEntry);
        assertThat(veCalculator.getCount()).isEqualTo(1L);

        Map<String, Long> entries = calculator.getDownloadSizePerFile(apkWithVirtualEntry);
        assertThat(entries.size()).isNotEqualTo(0);
        assertThat(entries.get(VIRTUAL_ENTRY_NAME)).isNull();
    }

    @Test
    public void getDownloadSizePerFile_filtersVirtualEntries() throws IOException {
        Path apkWithVirtualEntries =
                TestResources.getFile("/app_with_virtual_entries.apk").toPath();

        // Make sure the archive has more than one virtual entries.
        VirtualEntryCalculator veCalculator = new VirtualEntryCalculator(apkWithVirtualEntries);
        assertThat(veCalculator.getCount()).isEqualTo(3L);

        Map<String, Long> entries = calculator.getDownloadSizePerFile(apkWithVirtualEntries);
        assertThat(entries.size()).isNotEqualTo(0);
        assertThat(entries.get(VIRTUAL_ENTRY_NAME)).isNull();
    }

    @Test
    public void getInfoPerFile_filtersVirtualEntry() throws IOException {
        Path apkWithVirtualEntries = TestResources.getFile("/app_with_virtual_entry.apk").toPath();

        // Make sure the archive has more than one virtual entries.
        VirtualEntryCalculator veCalculator = new VirtualEntryCalculator(apkWithVirtualEntries);
        assertThat(veCalculator.getCount()).isEqualTo(1L);

        Map<String, ZipEntryInfo> entries = calculator.getInfoPerFile(apkWithVirtualEntries);
        assertThat(entries.size()).isNotEqualTo(0);
        assertThat(entries.get(VIRTUAL_ENTRY_NAME)).isNull();
    }

    @Test
    public void getInfoPerFile_filtersVirtualEntries() throws IOException {
        Path apkWithVirtualEntries =
                TestResources.getFile("/app_with_virtual_entries.apk").toPath();

        // Make sure the archive has more than one virtual entries.
        VirtualEntryCalculator veCalculator = new VirtualEntryCalculator(apkWithVirtualEntries);
        assertThat(veCalculator.getCount()).isEqualTo(3L);

        Map<String, ZipEntryInfo> entries = calculator.getInfoPerFile(apkWithVirtualEntries);
        assertThat(entries.size()).isNotEqualTo(0);
        assertThat(entries.get(VIRTUAL_ENTRY_NAME)).isNull();
    }

    public static boolean isVirtualEntry(String name) {
        return VIRTUAL_ENTRY_NAME.equals(name);
    }
}
