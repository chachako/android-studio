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
 *
 */
#ifndef RECOMPOSE_H
#define RECOMPOSE_H

#include <jni.h>
#include <jvmti.h>

#include <string>
#include <vector>

#include "tools/base/deploy/agent/native/class_finder.h"

#define HOT_RELOADER_CLASS "androidx/compose/runtime/HotReloader"
#define HOT_RELOADER_VMTYPE "Landroidx/compose/runtime/HotReloader$Companion;"

// 1.3.0 (see runtimeVersionToMavenVersionTable in runtime's VersionChecker.kt)
#define MIN_COMPOSE_RUNTIME_VERSION 8602

namespace deploy {

class Recompose {
 public:
  Recompose(jvmtiEnv* jvmti, JNIEnv* jni)
      : jvmti_(jvmti), jni_(jni), class_finder_(jvmti_, jni_) {}

  // Save state for Jetpack Compose before activity restart.
  jobject SaveStateAndDispose(jobject reloader) const;

  // Load state for Jetpack Compose after activity restart.
  void LoadStateAndCompose(jobject reloader, jobject state) const;

  // Invalidates a specific Compose group and trigger a recomposition.
  // Reference to the error string is changed to the error message or
  // empty string should there be no error messages.
  bool InvalidateGroupsWithKey(jobject reloader,
                               const std::vector<jint>& group_ids,
                               std::string& error) const;

  bool getCurrentErrors(jobject reloader, std::vector<bool>* recoverable,
                        std::vector<std::string>* exceptions,
                        std::string& error) const;

  bool VersionCheck(jobject reloader, std::string* error) const;

  // Create ComposeHotReload object if needed.
  jobject GetComposeHotReload() const;

  static const char* kComposeSupportClass;

 private:
  jvmtiEnv* jvmti_;
  JNIEnv* jni_;
  ClassFinder class_finder_;
};

}  // namespace deploy

#endif
