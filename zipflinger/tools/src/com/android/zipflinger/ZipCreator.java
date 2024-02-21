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
package com.android.zipflinger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ZipCreator {
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("Usage: zip [-flags] dest [files_to_zip]");
            return;
        }

        boolean recurse = false;
        boolean move = false;
        int argsIndex = 0;
        if (args[0].startsWith("-")) {
            for (int i = 1; i < args[0].length(); i++) {
                char flag = args[0].charAt(i);
                if (flag == 'r') {
                    recurse = true;
                }
                if (flag == 'm') {
                    move = true;
                }
            }
            argsIndex = 1;
        }
        Path dst = Paths.get(args[argsIndex++]);
        Files.deleteIfExists(dst);
        List<Path> files = new ArrayList<>();
        try (ZipArchive archive = new ZipArchive(dst)) {
            for (int i = argsIndex; i < args.length; i++) {
                Path src = Paths.get(args[i]);
                List<Path> toAdd = List.of(src);
                if (recurse && Files.isDirectory(src)) {
                    toAdd = Files.walk(src).filter(Files::isRegularFile).collect(Collectors.toList());
                }
                for (Path path : toAdd) {
                    archive.add(new BytesSource(path, path.getFileName().toString(), 0));
                }
                files.addAll(toAdd);
            }
        }
        if (move) {
            for (Path file : files) {
                Files.deleteIfExists(file);
            }
        }
    }
}
