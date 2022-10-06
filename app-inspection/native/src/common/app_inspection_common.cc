#include "app_inspection_common.h"

namespace app_inspection {

const char* ARTIFACT_COORDINATE_CLASS =
    "com/android/tools/agent/app/inspection/version/ArtifactCoordinate";
const std::string ARTIFACT_COORDINATE_TYPE =
    "L" + std::string(ARTIFACT_COORDINATE_CLASS) + ";";
const char* LIBRARY_COMPATIBILITY_CLASS =
    "com/android/tools/agent/app/inspection/version/LibraryCompatibility";
const std::string LIBRARY_COMPATIBILITY_TYPE =
    "L" + std::string(LIBRARY_COMPATIBILITY_CLASS) + ";";

jobject CreateArtifactCoordinate(JNIEnv* env, jstring group_id,
                                 jstring artifact_id, jstring version) {
  jclass clazz = env->FindClass(ARTIFACT_COORDINATE_CLASS);
  jmethodID constructor = env->GetMethodID(
      clazz, "<init>",
      "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
  return env->NewObject(clazz, constructor, group_id, artifact_id, version);
}

jobject CreateLibraryCompatibility(JNIEnv* env, jobject artifact,
                                   jobjectArray expected_library_class_names) {
  jclass clazz = env->FindClass(LIBRARY_COMPATIBILITY_CLASS);
  jmethodID constructor = env->GetMethodID(
      clazz, "<init>",
      ("(" + ARTIFACT_COORDINATE_TYPE + "[Ljava/lang/String;)V").c_str());
  return env->NewObject(clazz, constructor, artifact,
                        expected_library_class_names);
}

}  // namespace app_inspection
