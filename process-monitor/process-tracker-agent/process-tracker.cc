/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include <dirent.h>
#include <unistd.h>
#include <algorithm>
#include <set>
#include <string>

#include "utils.h"

namespace processtracker {

using namespace std;

class Scanner {
 public:
  /**
   * Reads processes from "/proc" and retrieves their name from
   * "/proc/<pid>/cmdline" and prints: When a process is added:   "+ <pid>
   * <process-name>" When a process is removed: "- <pid>" New processes are
   * added to the processes set and processes that not longer exists are removed
   * from it.
   */
  void scanProcesses() {
    // Whatever is left in this set needs to be removed from ::processes
    set<int> markedForRemoval(processes);

    DIR* pDir = opendir("/proc");
    while (true) {
      dirent* pDirent = readdir(pDir);
      if (pDirent == nullptr) {
        break;
      }

      int pid = parseInt(pDirent->d_name, -1);
      // Ignore entries that are not a valid pid
      if (pid < 0) {
        continue;
      }
      markedForRemoval.erase(pid);
      if (processes.find(pid) != processes.end()) {
        continue;
      }

      // New process found
      string path = string("/proc/") + pDirent->d_name;
      const string& name = readCommand(path);
      if (name.empty() || startsWith(name, "zygote") ||
          name == "<pre-initialized>") {
        // Ignore processes without a name or that haven't initialized yet
        continue;
      }
      processes.insert(pid);
      printf("+ %d %s\n", pid, name.c_str());
    }
    closedir(pDir);

    for (const int pid : markedForRemoval) {
      processes.erase(pid);
      printf("- %d\n", pid);
    }
    ::fflush(stdout);
  }

 private:
  set<int> processes;

  /**
   * Gets the name of the command
   *
   * The contents of "/proc/<pid>/cmdline" is a command line possibly followed
   * by '\0` chars.
   *
   * The command line can have arguments and the command can be a full path.
   *
   * This function extracts the filename of the command dropping off everything
   * else.
   */
  static string readCommand(const string& path) {
    const string& cmdline = readFile(path + "/cmdline");
    int start = 0;
    uint len = cmdline.length();
    uint end = len;
    for (int i = 0; i < len; i++) {
      const char& c = cmdline[i];
      if (c == '/') {
        start = i + 1;
        continue;
      }
      if (c == ' ' || c == '\0') {
        end = i;
        break;
      }
    }
    if (start - end > 0) {
      return cmdline.substr(start, end - start);
    }
    const string& comm = readFile(path + "/comm");
    return comm.substr(0, comm.find('\n'));
  }
};

}  // namespace processtracker

using namespace processtracker;

static int intervalMicros = 1000 * 1000;

static void printUsageAndExit(const string& message) {
  printf("%s\n", message.c_str());
  printf("Usage: process-tracker [-i|--interval <milliseconds>]\n");
  exit(EXIT_FAILURE);
}

static void parseCommandLine(int argc, char** argv) {
  for (int i = 1; i < argc; i++) {
    string arg(argv[i]);
    if (arg == "-i" || arg == "--interval") {
      i++;
      if (i >= argc) {
        printUsageAndExit("Missing argument for '" + arg + "'");
      }
      intervalMicros = parseInt(argv[i], -1) * 1000;
      if (intervalMicros <= 0) {
        printUsageAndExit(string("Invalid interval: ") + argv[i]);
      }
      continue;
    } else {
      printUsageAndExit("Invalid arg: " + arg);
    }
  }
}

/**
 * Runs an infinite loop and
 */
int main(int argc, char** argv) {
  parseCommandLine(argc, argv);

  Scanner scanner = Scanner();

#pragma clang diagnostic push
#pragma ide diagnostic ignored "EndlessLoop"
  while (true) {
    scanner.scanProcesses();
    usleep(intervalMicros);
  }
#pragma clang diagnostic pop
}
