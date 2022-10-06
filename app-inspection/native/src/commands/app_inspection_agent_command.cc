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

#include "app_inspection_agent_command.h"
#include "app_inspection_common.h"

#include "agent/agent.h"
#include "jvmti/jvmti_helper.h"

using app_inspection::AppInspectionCommand;
using app_inspection::ArtifactCoordinate;
using app_inspection::CreateInspectorCommand;
using app_inspection::DisposeInspectorCommand;
using app_inspection::LibraryCompatibility;
using profiler::Agent;
using profiler::proto::Command;

jobject createLibraryCompatibility(JNIEnv* jni_env,
                                   LibraryCompatibility compatibility);

void AppInspectionAgentCommand::RegisterAppInspectionCommandHandler(
    JavaVM* vm) {
  Agent::Instance().RegisterCommandHandler(
      Command::APP_INSPECTION, [vm](const Command* command) -> void {
        JNIEnv* jni_env = profiler::GetThreadLocalJNI(vm);
        jclass service_class = jni_env->FindClass(
            "com/android/tools/agent/app/inspection/"
            "AppInspectionService");
        jmethodID instance_method = jni_env->GetStaticMethodID(
            service_class, "instance",
            "()Lcom/android/tools/agent/app/inspection/"
            "AppInspectionService;");
        jobject service =
            jni_env->CallStaticObjectMethod(service_class, instance_method);

        if (service == nullptr) {
          // failed to instantiate AppInspectionService,
          // errors will have been logged indicating failures.
          return;
        }

        auto& app_command = command->app_inspection_command();
        int32_t command_id = app_command.command_id();
        jstring inspector_id =
            jni_env->NewStringUTF(app_command.inspector_id().c_str());
        if (app_command.has_create_inspector_command()) {
          auto& create_inspector = app_command.create_inspector_command();
          jstring dex_path =
              jni_env->NewStringUTF(create_inspector.dex_path().c_str());
          jstring project = jni_env->NewStringUTF(
              create_inspector.launch_metadata().launched_by_name().c_str());
          jboolean force = create_inspector.launch_metadata().force();

          jobject target = nullptr;
          if (create_inspector.launch_metadata().has_min_library()) {
            target = createLibraryCompatibility(
                jni_env, create_inspector.launch_metadata().min_library());
          }
          jmethodID create_inspector_method =
              jni_env->GetMethodID(service_class, "createInspector",
                                   ("(Ljava/lang/String;Ljava/lang/String;" +
                                    app_inspection::LIBRARY_COMPATIBILITY_TYPE +
                                    "Ljava/lang/String;ZI)V")
                                       .c_str());
          jni_env->CallVoidMethod(service, create_inspector_method,
                                  inspector_id, dex_path, target, project,
                                  force, command_id);
        } else if (app_command.has_dispose_inspector_command()) {
          auto& dispose_inspector = app_command.dispose_inspector_command();
          jmethodID dispose_inspector_method = jni_env->GetMethodID(
              service_class, "disposeInspector", "(Ljava/lang/String;I)V");
          jni_env->CallVoidMethod(service, dispose_inspector_method,
                                  inspector_id, command_id);
        } else if (app_command.has_raw_inspector_command()) {
          auto& raw_inspector_command = app_command.raw_inspector_command();
          const std::string& cmd = raw_inspector_command.content();
          jbyteArray raw_command = jni_env->NewByteArray(cmd.length());
          jni_env->SetByteArrayRegion(raw_command, 0, cmd.length(),
                                      (const jbyte*)cmd.c_str());
          jmethodID raw_inspector_method = jni_env->GetMethodID(
              service_class, "sendCommand", "(Ljava/lang/String;I[B)V");
          jni_env->CallVoidMethod(service, raw_inspector_method, inspector_id,
                                  command_id, raw_command);
          jni_env->DeleteLocalRef(raw_command);
        } else if (app_command.has_cancellation_command()) {
          auto& cancellation_command = app_command.cancellation_command();
          int32_t cancelled_command_id =
              cancellation_command.cancelled_command_id();
          jmethodID cancel_command_method =
              jni_env->GetMethodID(service_class, "cancelCommand", "(I)V");
          jni_env->CallVoidMethod(service, cancel_command_method,
                                  cancelled_command_id);
        } else if (app_command.has_get_library_compatibility_info_command()) {
          auto& get_library_compatibility_info_command =
              app_command.get_library_compatibility_info_command();
          int request_size =
              get_library_compatibility_info_command.target_libraries_size();
          jobjectArray targets = jni_env->NewObjectArray(
              request_size,
              jni_env->FindClass(app_inspection::LIBRARY_COMPATIBILITY_CLASS),
              NULL);

          for (int i = 0; i < request_size; ++i) {
            jobject target = createLibraryCompatibility(
                jni_env,
                get_library_compatibility_info_command.target_libraries(i));
            jni_env->SetObjectArrayElement(targets, i, target);
          }
          jmethodID get_library_versions_method = jni_env->GetMethodID(
              service_class, "getLibraryCompatibilityInfoCommand",
              ("(I[" + app_inspection::LIBRARY_COMPATIBILITY_TYPE + ")V")
                  .c_str());
          jni_env->CallVoidMethod(service, get_library_versions_method,
                                  command_id, targets);
        }
      });
}

jobject createLibraryCompatibility(JNIEnv* jni_env,
                                   LibraryCompatibility compatibility) {
  ArtifactCoordinate coordinate = compatibility.coordinate();
  jstring group_id = jni_env->NewStringUTF(coordinate.group_id().c_str());
  jstring artifact_id = jni_env->NewStringUTF(coordinate.artifact_id().c_str());
  jstring version = jni_env->NewStringUTF(coordinate.version().c_str());
  jobject target = app_inspection::CreateArtifactCoordinate(
      jni_env, group_id, artifact_id, version);

  jobjectArray class_names = nullptr;
  int class_count = compatibility.expected_library_class_names_size();
  if (class_count > 0) {
    class_names = jni_env->NewObjectArray(
        class_count, jni_env->FindClass("java/lang/String"), NULL);
    for (int i = 0; i < class_count; i++) {
      jni_env->SetObjectArrayElement(
          class_names, i,
          jni_env->NewStringUTF(
              compatibility.expected_library_class_names(i).c_str()));
    }
  }
  return app_inspection::CreateLibraryCompatibility(jni_env, target,
                                                    class_names);
}
