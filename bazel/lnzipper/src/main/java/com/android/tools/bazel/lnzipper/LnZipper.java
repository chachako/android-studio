/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.bazel.lnzipper;

import static com.android.zipflinger.Source.UNX_IRGRP;
import static com.android.zipflinger.Source.UNX_IROTH;
import static com.android.zipflinger.Source.UNX_IRUSR;
import static com.android.zipflinger.Source.UNX_IWGRP;
import static com.android.zipflinger.Source.UNX_IWOTH;
import static com.android.zipflinger.Source.UNX_IWUSR;
import static com.android.zipflinger.Source.UNX_IXGRP;
import static com.android.zipflinger.Source.UNX_IXOTH;
import static com.android.zipflinger.Source.UNX_IXUSR;

import com.android.zipflinger.FullFileSource;
import com.android.zipflinger.Source;
import com.android.zipflinger.Sources;
import com.android.zipflinger.ZipArchive;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.Deflater;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * Creates a ZIP archive, using zipflinger, optionally allowing the preservation of symbolic links.
 *
 * <p>If the zip_path is not given for a file, it is assumed to be the path of the file.
 *
 * <p>Usage: lnzipper [--compress] [--symlinks] [--attributes] [(@argfile | [zip_path[attr]=]file
 * ...)]
 *
 * <p>Ex. lnzipper --compress path/in/zip=/path/on/disk lnzipper --compress
 * path/in/zip[664]=/path/on/disk
 */
public class LnZipper {

    /** Entry point for the lnzipper binary. */
    public static void main(String[] args) {
        Options cliOptions = getCliOptions();
        HelpFormatter helpFormatter = new HelpFormatter();
        try {
            CommandLine commandLine = new DefaultParser().parse(cliOptions, args);
            new LnZipper(commandLine).execute();
        } catch (ParseException | IllegalArgumentException e) {
            if (e instanceof IllegalArgumentException) {
                System.err.printf("Error: %s\n", e.getMessage());
            }
            helpFormatter.printHelp(CMD_SYNTAX, cliOptions);
            System.exit(1);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static final String CMD_SYNTAX =
            "lnzipper [options] foo.zip [(@argfile | [zip_path[attr]=]file ...)]";

    public static final Option compress =
            Option.builder("C")
                    .argName("compress")
                    .desc("Compress files when creating a new archive")
                    .build();

    public static final Option create =
            Option.builder("c").argName("create").desc("Create a new archive").build();

    public static final Option symlinks =
            Option.builder("s")
                    .argName("symlinks")
                    .longOpt("symlinks")
                    .desc("Whether to preserve symlinks in new archives")
                    .build();

    public static final Option attributes =
            Option.builder("a")
                    .argName("attributes")
                    .longOpt("attributes")
                    .desc("Whether to preserve attributes in new archives")
                    .build();

    static Options getCliOptions() {
        Options opts = new Options();
        opts.addOption(compress);
        opts.addOption(create);
        opts.addOption(symlinks);
        opts.addOption(attributes);
        return opts;
    }

    private final CommandLine cmdLine;

    public LnZipper(CommandLine cmdLine) {
        this.cmdLine = cmdLine;
    }

    public void execute() throws IOException {
        List<String> argList = cmdLine.getArgList();
        if (!cmdLine.hasOption(create.getOpt())) {
            throw new IllegalArgumentException("Only the create action is supported");
        }
        if (argList.size() < 1) {
            throw new IllegalArgumentException("Missing ZIP archive argument");
        }
        Path archive = Paths.get(argList.get(0));
        Map<String, Entry> fileMap = getFileMapping(argList);

        createArchive(
                archive,
                fileMap,
                cmdLine.hasOption(compress.getOpt()),
                cmdLine.hasOption(symlinks.getOpt()),
                cmdLine.hasOption(attributes.getOpt()));
    }

    private static void createArchive(
            Path dest,
            Map<String, Entry> fileMap,
            boolean compress,
            boolean preserveSymlinks,
            boolean preserveAttributes)
            throws IOException {
        try (ZipArchive archive = new ZipArchive(dest)) {
            BytesSourceFactory bytesSourceFactory =
                    preserveSymlinks
                            ? (file, entryName, compressionLevel) ->
                                    new FullFileSource(
                                            file,
                                            entryName,
                                            compressionLevel,
                                            FullFileSource.Symlink.DO_NOT_FOLLOW)
                            : Sources::from;
            int compressionLevel = compress ? Deflater.BEST_COMPRESSION : Deflater.NO_COMPRESSION;

            for (Map.Entry<String, Entry> fileEntry : fileMap.entrySet()) {
                Source source =
                        bytesSourceFactory.create(
                                fileEntry.getValue().path, fileEntry.getKey(), compressionLevel);
                int attr = source.getExternalAttributes();
                if (preserveAttributes) {
                    attr = readFileAttributes(attr, fileEntry.getValue().path);
                }
                if (fileEntry.getValue().attr != 0) {
                    // Override file attributes, but leave anything else the same
                    attr = attr & ~(0x1FF0000);
                    attr |= (fileEntry.getValue().attr & 0x1FF) << 16;
                }
                source.setExternalAttributes(attr);
                archive.add(source);
            }
        }
    }

    private static int readFileAttributes(int attr, Path path) throws IOException {
        // Keep the link attribute as decided before
        attr = attr & Source.TYPE_FLNK;
        if (Files.isDirectory(path)) {
            attr |= Source.TYPE_FDIR;
        } else {
            attr |= Source.TYPE_FREG;
        }
        try {
            attr |= toExternalAttribute(Files.getPosixFilePermissions(path));
        } catch (UnsupportedOperationException e) {
            attr |= Source.PERMISSION_DEFAULT;
        }
        return attr;
    }

    /** Returns a mapping of ZIP entry name -> file path */
    private static Map<String, Entry> getFileMapping(List<String> args) throws IOException {
        if (args.size() < 2) {
            throw new IllegalArgumentException("No file inputs given");
        }
        List<String> fileArgs = new ArrayList<>();
        if (args.get(1).startsWith("@")) {
            BufferedReader bufReader = Files.newBufferedReader(Paths.get(args.get(1).substring(1)));
            String line = bufReader.readLine();
            while (line != null) {
                fileArgs.add(line);
                line = bufReader.readLine();
            }
        } else {
            fileArgs.addAll(args.subList(1, args.size()));
        }
        Map<String, Entry> fileMap = new LinkedHashMap<>();
        for (String fileArg : fileArgs) {
            Entry entry = new Entry();
            // check if file arguments are in the format zip_path=file
            if (fileArg.contains("=")) {
                String[] split = fileArg.split("=");
                entry.path = Paths.get(split[1]);
                String entryPath = split[0];
                if (entryPath.endsWith("]")) {
                    int ix = entryPath.lastIndexOf('[');
                    String octal = entryPath.substring(ix + 1, entryPath.length() - 1);
                    if (octal.length() != 3) {
                        throw new IllegalArgumentException("Permissions must be of length 3.");
                    }
                    entry.attr = Integer.parseInt(octal, 8);
                    entryPath = entryPath.substring(0, ix);
                }
                fileMap.put(entryPath, entry);
            } else {
                entry.path = Paths.get(fileArg);
                fileMap.put(fileArg, entry);
            }
        }

        return Collections.unmodifiableMap(fileMap);
    }

    private static class Entry {
        public Path path;
        public int attr;
    }

    private interface BytesSourceFactory {

        Source create(Path file, String entryName, int compressionLevel) throws IOException;
    }

    public static int toExternalAttribute(Set<PosixFilePermission> permissions) {
        int mask = 0;
        for (PosixFilePermission p : permissions) {
            switch (p) {
                case OWNER_READ:
                    mask |= UNX_IRUSR;
                    break;
                case OWNER_WRITE:
                    mask |= UNX_IWUSR;
                    break;
                case OWNER_EXECUTE:
                    mask |= UNX_IXUSR;
                    break;
                case GROUP_READ:
                    mask |= UNX_IRGRP;
                    break;
                case GROUP_WRITE:
                    mask |= UNX_IWGRP;
                    break;
                case GROUP_EXECUTE:
                    mask |= UNX_IXGRP;
                    break;
                case OTHERS_READ:
                    mask |= UNX_IROTH;
                    break;
                case OTHERS_WRITE:
                    mask |= UNX_IWOTH;
                    break;
                case OTHERS_EXECUTE:
                    mask |= UNX_IXOTH;
                    break;
            }
        }
        return mask;
    }
}
