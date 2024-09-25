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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.apk.analyzer.ArchiveContext;
import com.android.tools.apk.analyzer.ArchiveEntry;
import com.android.tools.apk.analyzer.ArchiveNode;
import com.android.tools.apk.analyzer.ArchiveTreeStructure;
import com.android.tools.apk.analyzer.PathUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;

import javax.swing.tree.DefaultMutableTreeNode;

public class ApkDiffParser {
    @NonNull
    public static DefaultMutableTreeNode createTreeNode(
            @NonNull ArchiveContext oldFile, @NonNull ArchiveContext newFile) throws IOException {
        ArchiveNode oldRoot = ArchiveTreeStructure.create(oldFile);
        GzipSizeCalculator calculator = new GzipSizeCalculator();
        ArchiveTreeStructure.updateFileInfo(oldRoot, calculator);
        ArchiveNode newRoot = ArchiveTreeStructure.create(newFile);
        ArchiveTreeStructure.updateFileInfo(newRoot, calculator);
        return createTreeNode(oldRoot, newRoot);
    }

    @NonNull
    private static DefaultMutableTreeNode createTreeNode(
            @Nullable ArchiveNode oldFile, @Nullable ArchiveNode newFile) throws IOException {
        if (oldFile == null && newFile == null) {
            throw new IllegalArgumentException("Both old and new files are null");
        }

        DefaultMutableTreeNode node = new DefaultMutableTreeNode();

        long oldSize = getSize(oldFile);
        long newSize = getSize(newFile);

        HashSet<String> childrenInOldFile = new HashSet<>();
        final ArchiveEntry data = oldFile == null ? newFile.getData() : oldFile.getData();
        final String name =
                data.getPath().getFileName() != null
                        ? PathUtils.fileNameWithTrailingSeparator(data.getPath())
                        : PathUtils.fileNameWithTrailingSeparator(data.getArchive().getPath());
        if (oldFile != null) {
            if (!oldFile.getChildren().isEmpty()) {
                for (ArchiveNode oldChild : oldFile.getChildren()) {
                    String fileName = oldChild.getData().getPath().getFileName().toString();
                    ArchiveNode newChild = findChildByFileName(newFile, fileName);
                    childrenInOldFile.add(fileName);
                    node.add(createTreeNode(oldChild, newChild));
                }
            }
        }
        if (newFile != null) {
            if (!newFile.getChildren().isEmpty()) {
                for (ArchiveNode newChild : newFile.getChildren()) {
                    if (childrenInOldFile.contains(
                            newChild.getData().getPath().getFileName().toString())) {
                        continue;
                    }

                    DefaultMutableTreeNode childNode = createTreeNode(null, newChild);
                    node.add(childNode);
                }
            }
        }

        node.setUserObject(new ApkDiffEntry(name, oldFile, newFile, oldSize, newSize));
        ApkEntry.sort(node);
        return node;
    }

    private static long getSize(ArchiveNode node) throws IOException {
        if (node == null) {
            // Node doesn't exist, size == 0
            return 0L;
        }
        if (node.getParent() == null) {
            // The APK itself. Use size on disk rather than compressed size
            return Files.size(node.getData().getArchive().getPath());
        }
        // Compressed size for everything else.
        return node.getData().getRawFileSize();
    }

    private static @Nullable ArchiveNode findChildByFileName(
            @Nullable ArchiveNode parent, String fileName) {
        if (parent == null) {
            return null;
        }
        for (ArchiveNode child : parent.getChildren()) {
            String name = child.getData().getPath().getFileName().toString();
            if (name.equals(fileName)) {
                return child;
            }
        }
        return null;
    }
}
