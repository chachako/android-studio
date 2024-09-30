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

package com.android.tools.apk.analyzer.internal;

import static com.android.tools.apk.analyzer.ZipEntryInfo.Alignment.ALIGNMENT_16K;
import static com.android.tools.apk.analyzer.ZipEntryInfo.Alignment.ALIGNMENT_4K;
import static com.android.tools.apk.analyzer.ZipEntryInfo.Alignment.ALIGNMENT_NONE;

import com.android.annotations.NonNull;
import com.android.tools.apk.analyzer.ApkSizeCalculator;
import com.android.tools.apk.analyzer.ZipEntryInfo;
import com.android.zipflinger.Entry;
import com.android.zipflinger.ZipRepo;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import com.google.common.io.CountingOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.logging.Logger;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

public class GzipSizeCalculator implements ApkSizeCalculator {

    private static final long OFFSET_4K = 4 * 1024;

    private static final long OFFSET_16K = 16 * 1024;

    public GzipSizeCalculator() {}

    private static void verify(@NonNull Path apk) {
        //noinspection EmptyTryBlock,unused
        try (ZipRepo zip = new ZipRepo(apk)) {
        } catch (IOException e) {
            // Ignore exceptions if the file doesn't exist (b/351919218)
            if (Files.exists(apk)) {
                throw new IllegalArgumentException("Cannot open apk: ", e);
            }
        }
    }

    @Override
    public long getFullApkDownloadSize(@NonNull Path apk) {
        verify(apk);
        // There is a difference between uncompressing the apk, and then compressing again using
        // "gzip -9", versus just compressing the apk itself using "gzip -9". But the difference
        // seems to be negligible, and we are only aiming at an estimate of what Play provides, so
        // this should suffice. This also seems to be the same approach taken by
        // https://github.com/googlesamples/apk-patch-size-estimator

        try (MaxGzipCountingOutputStream zos = new MaxGzipCountingOutputStream()) {
            Files.copy(apk, zos);
            return zos.getSize();
        } catch (IOException e) {
            return -1;
        }
    }

    @Override
    public long getFullApkRawSize(@NonNull Path apk) {
        verify(apk);
        try {
            return Files.size(apk);
        } catch (IOException e) {
            Logger.getLogger(GzipSizeCalculator.class.getName())
                    .severe("Error obtaining size of the APK: " + e);
            return -1;
        }
    }

    @NonNull
    @Override
    public Map<String, Long> getDownloadSizePerFile(@NonNull Path apk) {
        verify(apk);
        try (ZipRepo zipRepo = new ZipRepo(apk)) {
            ImmutableMap.Builder<String, Long> sizes = new ImmutableMap.Builder<>();
            for (Entry entry : zipRepo.getEntries().values()) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                try (InputStream zip = zipRepo.getInputStream(name);
                        MaxGzipCountingOutputStream gzip = new MaxGzipCountingOutputStream()) {
                    ByteStreams.copy(zip, gzip);
                    sizes.put("/" + name, gzip.getSize());
                }
            }
            return sizes.build();
        } catch (IOException e) {
            String msg =
                    "Error while re-compressing apk to determine file by file download sizes: " + e;
            Logger.getLogger(GzipSizeCalculator.class.getName()).severe(msg);
            return ImmutableMap.of();
        }
    }

    @NonNull
    @Override
    public Map<String, ZipEntryInfo> getInfoPerFile(@NonNull Path apk) {
        verify(apk);
        ImmutableMap.Builder<String, ZipEntryInfo> sizes = new ImmutableMap.Builder<>();

        try (ZipRepo zip = new ZipRepo(apk)) {
            Collection<Entry> entries = zip.getEntries().values();
            for (Entry entry : entries) {
                if (entry.isDirectory()) {
                    continue;
                }
                long size = entry.getCompressedSize();
                boolean isCompressed = entry.isCompressed();
                long location = entry.getPayloadLocation().first;
                final ZipEntryInfo.Alignment alignment;
                if (location % OFFSET_16K == 0) {
                    alignment = ALIGNMENT_16K;
                } else if (location % OFFSET_4K == 0) {
                    alignment = ALIGNMENT_4K;
                } else {
                    alignment = ALIGNMENT_NONE;
                }
                sizes.put("/" + entry.getName(), new ZipEntryInfo(size, alignment, isCompressed));
            }
        } catch (IOException ignored) {
        }

        return sizes.build();
    }

    private static final class MaxGzipCountingOutputStream extends GZIPOutputStream {

        public MaxGzipCountingOutputStream() throws IOException {
            super(new CountingOutputStream(ByteStreams.nullOutputStream()));
            // Google Play serves an APK that is compressed using gzip -9
            def.setLevel(Deflater.BEST_COMPRESSION);
        }

        public long getSize() throws IOException {
            close();
            return ((CountingOutputStream) out).getCount();
        }
    }
}
