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

#pragma once

#include <cstring>
#include <fstream>
#include <sstream>
#include <string>

namespace processtracker {

using namespace std;

/** Reads the contents of a file into a string  */
string readFile(const string& path) {
  ifstream stream(path);
  stringstream buffer;
  buffer << stream.rdbuf();
  return buffer.str();
}

/**
 * Returns true if str starts with prefix.
 *
 * Note that this uses rfind(str, 0) to avoid scanning the entire string.
 */
bool startsWith(const string& str, const string& prefix) {
  return str.rfind(prefix, 0) == 0;
}

/**
 * Parses a string into an integer.
 *
 * If the string doesn't represent a valid integer, returns a default value.
 */
int parseInt(const char* str, int defaultValue) {
  char* ptr;
  long l = strtol(str, &ptr, 10);
  if (*ptr != '\0' || l > INT32_MAX) {
    return defaultValue;
  }
  return static_cast<int>(l);
}

}  // namespace processtracker
