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
package com.android.resources.aar;

import com.android.annotations.NonNull;
import com.android.utils.Base128OutputStream;
import com.google.common.annotations.VisibleForTesting;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * A command-line program for packaging framework resources into framework_res.jar. The jar file
 * created by this program contains compressed XML resource files and two binary files,
 * resources.bin and resources_light.bin. Format of these binary files is identical to format of
 * a framework resource cache file without a header. The resources.bin file contains a list of all
 * framework resources. The resources_light.bin file contains a list of resources excluding
 * locale-specific ones.
 */
@SuppressWarnings({"UseOfSystemOutOrSystemErr", "CallToPrintStackTrace"})
public class FrameworkResJarCreator {
  public static void main(@NonNull String[] args) {
    if (args.length != 2) {
      printUsage(FrameworkResJarCreator.class.getName());
      System.exit(1);
    }

    Path resDirectory = Paths.get(args[0]).toAbsolutePath().normalize();
    Path jarFile = Paths.get(args[1]).toAbsolutePath().normalize();
    try {
      createJar(resDirectory, jarFile);
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }

  @VisibleForTesting
  static void createJar(@NonNull Path resDirectory, @NonNull Path jarFile) throws IOException {
    FrameworkResourceRepository repository = FrameworkResourceRepository.create(resDirectory, null, null, false);
    Set<String> languages = repository.getLanguageGroups();

    try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jarFile))) {
      for (String language : languages) {
        String entryName = FrameworkResourceRepository.getResourceTableNameForLanguage(language);
        createZipEntry(entryName, getEncodedResources(repository, language), zip);
      }

      Path parentDir = resDirectory.getParent();
      List<Path> files = getContainedFiles(resDirectory);

      for (Path file : files) {
        // When running on Windows, we need to make sure that the file entries are correctly encoded
        // with the Unix path separator since the ZIP file spec only allows for that one.
        String relativePath = parentDir.relativize(file).toString().replace('\\', '/');
        if (!relativePath.equals("res/version") && !relativePath.equals("res/BUILD")) { // Skip "version" and "BUILD" files.
          createZipEntry(relativePath, Files.readAllBytes(file), zip);
        }
      }
    }
  }

  @NonNull
  private static List<Path> getContainedFiles(@NonNull Path resDirectory) throws IOException {
    List<Path> files = new ArrayList<>();
    Files.walkFileTree(resDirectory, new SimpleFileVisitor<Path>() {
      @Override
      @NonNull
      public FileVisitResult visitFile(@NonNull Path file, @NonNull BasicFileAttributes attrs) {
        files.add(file);
        return FileVisitResult.CONTINUE;
      }
    });
    Collections.sort(files); // Make sure that the files are in canonical order.
    return files;
  }

  private static void createZipEntry(@NonNull String name, @NonNull byte[] content, @NonNull ZipOutputStream zip) throws IOException {
    ZipEntry entry = new ZipEntry(name);
    zip.putNextEntry(entry);
    zip.write(content);
    zip.closeEntry();
  }

  @NonNull
  private static byte[] getEncodedResources(@NonNull FrameworkResourceRepository repository, @NonNull String language) throws IOException {
    ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
    try (Base128OutputStream stream = new Base128OutputStream(byteStream)) {
      repository.writeToStream(stream, config -> language.equals(FrameworkResourceRepository.getLanguageGroup(config)));
    }
    return byteStream.toByteArray();
  }

  private static void printUsage(@NonNull String programName) {
    System.out.printf("Usage: %s <res_directory> <jar_file>%n", programName);
  }
}
