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
#include <cstring>
#include <fstream>
#include <set>
#include <sstream>
#include <string>

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
    set<string> markedForRemoval(processes);

    DIR* pDir = opendir("/proc");
    while (true) {
      dirent* pDirent = readdir(pDir);
      if (pDirent == nullptr) {
        break;
      }

      // Ignore entries that are not a valid pid
      if (!isNumber(pDirent->d_name)) {
        continue;
      }
      string pid(pDirent->d_name);
      markedForRemoval.erase(pid);
      if (processes.find(pid) != processes.end()) {
        continue;
      }

      // New process found
      string path = string("/proc/") + pDirent->d_name + "/cmdline";
      const string& name = readCommand(path);
      if (name.empty()) {
        // Ignore processes without a name
        continue;
      }
      processes.insert(pid);
      printf("+ %s %s\n", pid.c_str(), name.c_str());
    }
    closedir(pDir);

    for (const string& pid : markedForRemoval) {
      processes.erase(pid);
      printf("- %s\n", pid.c_str());
    }
    ::fflush(stdout);
  }

 private:
  set<string> processes;

  /** Returns true if the string is comprised of digits */
  static bool isNumber(const char* s) {
    for (size_t i = 0, n = strlen(s); i < n; i++) {
      if (!isdigit(s[i])) {
        return false;
      }
    }
    return true;
  }

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
    ifstream t(path);
    stringstream buffer;
    buffer << t.rdbuf();
    const string& cmdline = buffer.str();
    int start = 0;
    uint len = cmdline.length();
    uint end = len;
    if (cmdline.find("chrome") != string::npos) {
      end = len;
    }
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
    return cmdline.substr(start, end - start);
  }
};
}  // namespace processtracker

using namespace processtracker;

static long intervalMicros = 1000 * 1000;

static void printUsageAndExit(const string& message) {
  printf("%s\n", message.c_str());
  printf("Usage: process-tracker [-f|--frequency <milliseconds>]\n");
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
      char* ptr;
      intervalMicros = strtol(argv[i], &ptr, 10);
      if (*ptr != '\0' || intervalMicros <= 0) {
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
