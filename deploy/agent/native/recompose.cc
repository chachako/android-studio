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

#include "tools/base/deploy/agent/native/recompose.h"

#include <jni.h>
#include <jvmti.h>
#include <string.h>

#include "tools/base/deploy/agent/native/jni/jni_class.h"
#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/common/log.h"

namespace deploy {

const char* Recompose::kComposeSupportClass =
    "com/android/tools/deploy/liveedit/ComposeSupport";

// Can be null if the application isn't a JetPack Compose application.
jobject Recompose::GetComposeHotReload() const {
  jclass klass = class_finder_.FindInClassLoader(
      class_finder_.GetApplicationClassLoader(), HOT_RELOADER_CLASS);
  if (klass == nullptr) {
    return nullptr;
  }
  JniClass reloaderClass(jni_, klass);
  return reloaderClass.GetStaticObjectField("Companion", HOT_RELOADER_VMTYPE);
}

jobject Recompose::SaveStateAndDispose(jobject reloader) const {
  JniObject reloader_jnio(jni_, reloader);
  JniClass activity_thread(jni_, "android/app/ActivityThread");
  jobject context = activity_thread.CallStaticObjectMethod(
      "currentApplication", "()Landroid/app/Application;");

  jmethodID mid =
      jni_->GetMethodID(reloader_jnio.GetClass(), "saveStateAndDispose",
                        "(Ljava/lang/Object;)Ljava/lang/Object;");
  if (mid == nullptr) {
    ErrEvent("saveStateAndDispose(Object) not found.");
    // GetMethodID isn't a Java method but ART do throw a Java Exception.
    if (jni_->ExceptionCheck()) {
      jni_->ExceptionClear();
    }
    return nullptr;
  }

  jobject state = reloader_jnio.CallObjectMethod(
      "saveStateAndDispose", "(Ljava/lang/Object;)Ljava/lang/Object;", context);

  if (jni_->ExceptionCheck()) {
    ErrEvent("Exception During SaveStateAndDispose");
    jni_->ExceptionDescribe();
    jni_->ExceptionClear();
    return nullptr;
  }

  return state;
}

void Recompose::LoadStateAndCompose(jobject reloader, jobject state) const {
  Log::V("Performing LoadStateAndCompose.");
  if (state == nullptr) {
    ErrEvent("Unable to LoadStateAndCompose. state is null.");
    return;
  }

  JniObject reloader_jnio(jni_, reloader);
  jmethodID mid = jni_->GetMethodID(
      reloader_jnio.GetClass(), "loadStateAndCompose", "(Ljava/lang/Object;)V");
  if (mid == nullptr) {
    ErrEvent("loadStateAndCompose(Object) not found.");
    // GetMethodID isn't a Java method but ART do throw a Java Exception.
    if (jni_->ExceptionCheck()) {
      jni_->ExceptionClear();
    }
    return;
  }

  reloader_jnio.CallVoidMethod("loadStateAndCompose", "(Ljava/lang/Object;)V",
                               state);

  if (jni_->ExceptionCheck()) {
    ErrEvent("Exception During loadStateAndCompose");
    jni_->ExceptionDescribe();
    jni_->ExceptionClear();
  }
}

bool Recompose::InvalidateGroupsWithKey(jobject reloader,
                                        const std::vector<jint>& group_ids,
                                        std::string& error) const {
  JniClass support(jni_, Recompose::kComposeSupportClass);
  JniObject reloader_jnio(jni_, reloader);
  jintArray j_group_ids = jni_->NewIntArray(group_ids.size());
  jni_->SetIntArrayRegion(j_group_ids, 0, group_ids.size(), group_ids.data());

  jstring jresult = (jstring)support.CallStaticObjectMethod(
      "recomposeFunction", "(Ljava/lang/Object;[I)Ljava/lang/String;", reloader,
      j_group_ids);

  if (jni_->ExceptionCheck()) {
    jni_->ExceptionDescribe();
    jni_->ExceptionClear();
    error = "Exception During invalidateGroupsWithKey";
    return false;
  }

  const char* cresult = jni_->GetStringUTFChars(jresult, JNI_FALSE);
  std::string result(cresult);
  jni_->ReleaseStringUTFChars(jresult, cresult);

  if (!result.empty()) {
    error = result;
    return false;
  }
  return true;
}

bool Recompose::getCurrentErrors(jobject reloader,
                                 std::vector<bool>* recoverable,
                                 std::vector<std::string>* exceptions,
                                 std::string& error) const {
  JniClass support(jni_, Recompose::kComposeSupportClass);

  // If this method is called after the app is restarted - which can happen
  // because studio repeatedly attaches the agent after every LiveEdit - the
  // agent may not have set up the instrumentation jar yet, which can cause this
  // class to be missing.
  if (!support.isValid()) {
    jni_->ExceptionClear();
    return true;
  }

  JniObject reloader_jnio(jni_, reloader);

  jobjectArray jresult = (jobjectArray)support.CallStaticObjectMethod(
      "fetchPendingErrors",
      "(Ljava/lang/Object;)[Lcom/android/tools/deploy/liveedit/"
      "ComposeSupport$LiveEditRecomposeError;",
      reloader);

  if (jresult == NULL) {
    error = "getCurrentErrors Failure";
    return false;
  }

  if (jni_->ExceptionCheck()) {
    jni_->ExceptionDescribe();
    jni_->ExceptionClear();
    error = "Exception During getCurrentErrors";
    return false;
  }

  jsize len = jni_->GetArrayLength(jresult);

  for (int i = 0; i < len; i++) {
    jobject exception = jni_->GetObjectArrayElement(jresult, i);
    jclass wrapper_class = jni_->GetObjectClass(exception);
    jfieldID recoverable_field =
        jni_->GetFieldID(wrapper_class, "recoverable", "Z");

    if (jni_->ExceptionCheck()) {
      jni_->ExceptionDescribe();
      jni_->ExceptionClear();
      error = "Exception fetching recoverable status of a Compose exception.";
      return false;
    }
    bool re = jni_->GetBooleanField(exception, recoverable_field);

    jfieldID exception_field =
        jni_->GetFieldID(wrapper_class, "cause", "Ljava/lang/String;");
    jstring jex = (jstring)jni_->GetObjectField(exception, exception_field);

    if (jni_->ExceptionCheck()) {
      jni_->ExceptionDescribe();
      jni_->ExceptionClear();
      error = "Exception fetching cause of a Compose exception.";
      return false;
    }
    const char* cresult = jni_->GetStringUTFChars(jex, JNI_FALSE);
    std::string result(cresult);

    recoverable->push_back(re);
    exceptions->push_back(result);
    jni_->ReleaseStringUTFChars(jex, cresult);
  }

  return true;
}

bool Recompose::VersionCheck(jobject reloader, std::string* error) const {
  JniClass support(jni_, Recompose::kComposeSupportClass);
  JniObject reloader_jnio(jni_, reloader);

  jstring jresult = (jstring)support.CallStaticObjectMethod(
      "versionCheck", "(Ljava/lang/Object;I)Ljava/lang/String;", reloader,
      MIN_COMPOSE_RUNTIME_VERSION);

  if (jni_->ExceptionCheck()) {
    jni_->ExceptionDescribe();
    jni_->ExceptionClear();
    *error = "Exception During versionCheck";
    return false;
  }

  const char* cresult = jni_->GetStringUTFChars(jresult, JNI_FALSE);
  std::string result(cresult);
  jni_->ReleaseStringUTFChars(jresult, cresult);

  if (!result.empty()) {
    *error = result;
    return false;
  }
  return true;
}

}  // namespace deploy
