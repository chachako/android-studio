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
#include "perfd/common/trace_manager.h"

#include "utils/stopwatch.h"

using profiler::proto::TraceConfiguration;
using profiler::proto::TraceStartStatus;
using profiler::proto::TraceStopStatus;
using profiler::proto::UserOptions;

namespace profiler {

static const int16_t kTimestampNotSet = -1;
static const int64_t kTraceRecordBufferSize = 10;

CaptureInfo* TraceManager::StartCapture(
    int64_t request_timestamp_ns,
    const proto::TraceConfiguration& configuration, TraceStartStatus* status,
    const bool use_unified_config) {
  std::lock_guard<std::recursive_mutex> lock(capture_mutex_);

  const auto& app_name = configuration.app_name();
  // obtain the CircularBuffer, create in place if one does not exist already.
  CircularBuffer<CaptureInfo>& cache =
      capture_cache_
          .emplace(std::piecewise_construct, std::forward_as_tuple(app_name),
                   std::forward_as_tuple(kTraceRecordBufferSize))
          .first->second;
  // Early-out if there is an ongoing previous capture.
  if (!cache.empty() && cache.back().end_timestamp == kTimestampNotSet) {
    status->set_status(TraceStartStatus::FAILURE);
    status->set_error_message("ongoing capture already exists");
    return nullptr;
  }

  status->set_status(TraceStartStatus::SUCCESS);
  std::string error_message;
  bool success = false;
  if (configuration.initiation_type() == proto::INITIATED_BY_API) {
    // Special case for API-initiated tracing: Only cache the CaptureInfo
    // record, as the trace logic is handled via the app.
    success = true;
  } else {
    // Note user_options.buffer_size_in_mb() isn't used here. It applies only to
    // ART tracing for pre-O which is not handled by the daemon.
    bool startup_profiling =
        configuration.initiation_type() == proto::INITIATED_BY_STARTUP;

    if (use_unified_config) {
      // Utilize technology-specific options in unified trace configuration
      // proto.
      switch (configuration.union_case()) {
        case TraceConfiguration::kArtOptions: {
          auto art_options = configuration.art_options();
          auto mode = art_options.trace_mode() == proto::TraceMode::INSTRUMENTED
                          ? ActivityManager::INSTRUMENTED
                          : ActivityManager::SAMPLING;
          success = activity_manager_->StartProfiling(
              mode, app_name, art_options.sampling_interval_us(),
              configuration.temp_path(), &error_message, startup_profiling);
          break;
        }
        case TraceConfiguration::kAtraceOptions: {
          auto atrace_options = configuration.atrace_options();
          int acquired_buffer_size_kb = 0;
          success = atrace_manager_->StartProfiling(
              app_name, kAtraceBufferSizeInMb, &acquired_buffer_size_kb,
              configuration.temp_path(), &error_message);
          break;
        }
        case TraceConfiguration::kSimpleperfOptions: {
          auto simpleperf_options = configuration.simpleperf_options();
          success = simpleperf_manager_->StartProfiling(
              app_name, configuration.abi_cpu_arch(),
              simpleperf_options.sampling_interval_us(),
              configuration.temp_path(), &error_message, startup_profiling);
          break;
        }
        case TraceConfiguration::kPerfettoOptions: {
          auto perfetto_options = configuration.perfetto_options();
          success = perfetto_manager_->StartProfiling(
              app_name, configuration.abi_cpu_arch(), perfetto_options,
              configuration.temp_path(), &error_message);
          break;
        }
        default:
          success = false;
          error_message = "No technology-specific tracing options set.";
          break;
      }
    } else {
      // Utilize UserOptions based options in old trace configuration proto.
      const auto& user_options = configuration.user_options();
      if (user_options.trace_type() == UserOptions::SIMPLEPERF) {
        success = simpleperf_manager_->StartProfiling(
            app_name, configuration.abi_cpu_arch(),
            user_options.sampling_interval_us(), configuration.temp_path(),
            &error_message, startup_profiling);
      } else if (user_options.trace_type() == UserOptions::ATRACE) {
        int acquired_buffer_size_kb = 0;
        success = atrace_manager_->StartProfiling(
            app_name, kAtraceBufferSizeInMb, &acquired_buffer_size_kb,
            configuration.temp_path(), &error_message);
      } else if (user_options.trace_type() == UserOptions::PERFETTO) {
        // Perfetto always acquires the proper buffer size.
        int acquired_buffer_size_kb = kPerfettoBufferSizeInMb * 1024;
        // TODO: We may want to pass this in from studio for a more flexible
        // config.
        perfetto::protos::TraceConfig config =
            PerfettoManager::BuildFtraceConfig(app_name,
                                               acquired_buffer_size_kb);
        success = perfetto_manager_->StartProfiling(
            app_name, configuration.abi_cpu_arch(), config,
            configuration.temp_path(), &error_message);
      } else {
        auto mode = user_options.trace_mode() == proto::TraceMode::INSTRUMENTED
                        ? ActivityManager::INSTRUMENTED
                        : ActivityManager::SAMPLING;
        success = activity_manager_->StartProfiling(
            mode, app_name, user_options.sampling_interval_us(),
            configuration.temp_path(), &error_message, startup_profiling);
      }
    }
  }

  if (success) {
    CaptureInfo capture;
    capture.trace_id = clock_->GetCurrentTime();
    capture.start_timestamp = request_timestamp_ns;
    // kTimestampNotSet for end timestamp means trace is ongoing
    capture.end_timestamp = kTimestampNotSet;
    capture.configuration = configuration;
    capture.start_status.CopyFrom(*status);

    return cache.Add(capture);
  } else {
    status->set_status(TraceStartStatus::FAILURE);
    status->set_error_message(error_message);
    return nullptr;
  }
}

/**
 * TODO: (b/259745930) Delete this method.
 * This temporary method was created to accommodate for the UserOptions
 * trace type reading done in StopCapturing. Tests created for the new
 * options fields were failing without the precense of an uncessary
 * UserOptions so this method handles not having UserOptions present.
 */
UserOptions::TraceType getTraceTypeFromCapture(TraceConfiguration config) {
  if (config.has_art_options()) {
    return UserOptions::ART;
  } else if (config.has_atrace_options()) {
    return UserOptions::ATRACE;
  } else if (config.has_simpleperf_options()) {
    return UserOptions::SIMPLEPERF;
  } else if (config.has_perfetto_options()) {
    return UserOptions::PERFETTO;
  }
  return config.user_options().trace_type();
}

CaptureInfo* TraceManager::StopCapture(int64_t request_timestamp_ns,
                                       const std::string& app_name,
                                       bool need_trace,
                                       TraceStopStatus* status) {
  std::lock_guard<std::recursive_mutex> lock(capture_mutex_);

  auto* ongoing_capture = GetOngoingCapture(app_name);
  if (ongoing_capture == nullptr) {
    status->set_error_message("No ongoing capture exists");
    status->set_status(TraceStopStatus::NO_ONGOING_PROFILING);
    return nullptr;
  }

  std::string error_message;
  TraceStopStatus::Status stop_status = TraceStopStatus::SUCCESS;
  if (ongoing_capture->configuration.initiation_type() ==
      proto::INITIATED_BY_API) {
    // Special for API-initiated tracing: only update the
    // CaptureInfo record, as the trace logic is handled via the app.
    // End timestamp should come from when the stop request was invoked
    // in the app.
    ongoing_capture->end_timestamp = request_timestamp_ns;
  } else {
    Stopwatch stopwatch;
    auto trace_type = getTraceTypeFromCapture(ongoing_capture->configuration);
    if (trace_type == UserOptions::SIMPLEPERF) {
      stop_status = simpleperf_manager_->StopProfiling(app_name, need_trace,
                                                       &error_message);
    } else if (trace_type == UserOptions::ATRACE) {
      stop_status =
          atrace_manager_->StopProfiling(app_name, need_trace, &error_message);
    } else if (trace_type == UserOptions::PERFETTO) {
      stop_status = perfetto_manager_->StopProfiling(&error_message);
    } else {  // Profiler is ART
      stop_status = activity_manager_->StopProfiling(
          app_name, need_trace, &error_message,
          cpu_config_.art_stop_timeout_sec(),
          ongoing_capture->configuration.initiation_type() ==
              proto::INITIATED_BY_STARTUP);
    }
    ongoing_capture->end_timestamp = clock_->GetCurrentTime();
    status->set_stopping_duration_ns(stopwatch.GetElapsed());
  }

  status->set_status(stop_status);
  status->set_error_message(error_message);
  ongoing_capture->stop_status.CopyFrom(*status);

  return ongoing_capture;
}

CaptureInfo* TraceManager::GetOngoingCapture(const std::string& app_name) {
  std::lock_guard<std::recursive_mutex> lock(capture_mutex_);

  auto itr = capture_cache_.find(app_name);
  if (itr == capture_cache_.end()) {
    return nullptr;
  }

  CircularBuffer<CaptureInfo>& cache = itr->second;
  if (!cache.empty() && cache.back().end_timestamp == kTimestampNotSet) {
    return &cache.back();
  }

  return nullptr;
}

std::vector<CaptureInfo> TraceManager::GetCaptures(const std::string& app_name,
                                                   int64_t from, int64_t to) {
  std::lock_guard<std::recursive_mutex> lock(capture_mutex_);

  std::vector<CaptureInfo> captures;
  auto itr = capture_cache_.find(app_name);
  if (itr == capture_cache_.end()) {
    return captures;
  }

  CircularBuffer<CaptureInfo>& cache = itr->second;
  for (size_t i = 0; i < cache.size(); i++) {
    const auto& candidate = cache.Get(i);
    // Skip completed captures that ends earlier than |from| and those
    // (completed or not) that starts after |to|.
    if ((candidate.end_timestamp != kTimestampNotSet &&
         candidate.end_timestamp < from) ||
        candidate.start_timestamp > to) {
      continue;
    }
    captures.push_back(candidate);
  }

  return captures;
}

}  // namespace profiler
