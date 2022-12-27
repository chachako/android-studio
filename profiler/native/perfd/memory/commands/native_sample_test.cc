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
#include "start_native_sample.h"
#include "stop_native_sample.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include "daemon/event_writer.h"
#include "perfd/common/atrace/fake_atrace.h"
#include "perfd/common/perfetto/fake_perfetto.h"
#include "perfd/common/perfetto/perfetto_manager.h"
#include "perfd/memory/native_heap_manager.h"
#include "utils/device_info.h"
#include "utils/device_info_helper.h"
#include "utils/fake_clock.h"
#include "utils/fs/memory_file_system.h"
#include "utils/termination_service.h"

#include <condition_variable>
#include <mutex>
#include <thread>
#include <vector>

using std::string;

using profiler::proto::TraceStartStatus;
using profiler::proto::TraceStopStatus;
using ::testing::_;
using ::testing::Return;

namespace profiler {
class MockNativeHeapManager final : public NativeHeapManager {
 public:
  explicit MockNativeHeapManager(FileCache* file_cache,
                                 PerfettoManager& perfetto_manager)
      : NativeHeapManager(file_cache, perfetto_manager) {}

  MOCK_CONST_METHOD3(StartSample,
                     bool(int64_t ongoing_capture_id,
                          const proto::StartNativeSample& start_command,
                          std::string* error_message));
  MOCK_CONST_METHOD2(StopSample,
                     bool(int64_t capture_id, std::string* error_message));
};

// Helper class to handle even streaming from the EventBuffer.
class TestEventWriter final : public EventWriter {
 public:
  TestEventWriter(std::vector<proto::Event>* events,
                  std::condition_variable* cv)
      : events_(events), cv_(cv) {}

  bool Write(const proto::Event& event) override {
    events_->push_back(event);
    cv_->notify_one();
    return true;
  }

 private:
  std::vector<proto::Event>* events_;
  std::condition_variable* cv_;
};

class NativeSampleTest : public testing::Test {
  public:
  NativeSampleTest()
      : clock_(), event_buffer_(&clock_) {}
  void SetUp() override {
    proto::DaemonConfig config_proto = proto::DaemonConfig::default_instance();
    DaemonConfig config(config_proto);

    DeviceInfoHelper::SetDeviceInfo(DeviceInfo::P);
    FileCache file_cache(std::unique_ptr<FileSystem>(new MemoryFileSystem()),
                         "/");
    daemon_ = std::unique_ptr<Daemon>(
        new Daemon(&clock_, &config, &file_cache, &event_buffer_));

    auto* manager = SessionsManager::Instance();
    proto::BeginSession begin_session;
    manager->BeginSession(daemon_.get(), 0, 0, begin_session);

    // Start the event writer to listen for incoming events on a separate
    // thread.
    writer_ =
        std::unique_ptr<TestEventWriter>(new TestEventWriter(&events_, &cv_));
    read_thread_ = std::unique_ptr<std::thread>(new std::thread(
        [this] { event_buffer_.WriteEventsTo(writer_.get()); }));
  }

  void TearDown() override {
    // Kill read thread to cleanly exit test.
    event_buffer_.InterruptWriteEvents();
    read_thread_->join();
    read_thread_ = nullptr;
    // Clean up any sessions we created.
    SessionsManager::Instance()->ClearSessions();
  }

  FakeClock clock_;
  EventBuffer event_buffer_;
  std::unique_ptr<Daemon> daemon_;
  std::vector<proto::Event> events_;
  std::condition_variable cv_;
  std::unique_ptr<std::thread> read_thread_;
  std::unique_ptr<TestEventWriter> writer_;
};

// Test that we receive the start and end events for a successful heap dump.
TEST_F(NativeSampleTest, CommandsGeneratesEvents) {
  DaemonConfig config(proto::DaemonConfig::default_instance());
  FileCache file_cache(std::unique_ptr<FileSystem>(new MemoryFileSystem()),
                       "/");
  std::mutex mutex;
  proto::Command command;
  auto* termination_service = TerminationService::Instance();
  profiler::proto::DaemonConfig::CpuConfig cpu_config;

  TraceManager trace_manager{
      &clock_,
      cpu_config,
      termination_service,
      ActivityManager::Instance(),
      std::unique_ptr<SimpleperfManager>(
          new SimpleperfManager(std::unique_ptr<Simpleperf>(new Simpleperf()))),
      std::unique_ptr<AtraceManager>(new AtraceManager(
          std::unique_ptr<FileSystem>(new MemoryFileSystem()), &clock_, 50,
          std::unique_ptr<Atrace>(new FakeAtrace(&clock_)))),
      std::unique_ptr<PerfettoManager>(
          new PerfettoManager(std::unique_ptr<Perfetto>(new FakePerfetto())))};

  // Execute the start command
  clock_.SetCurrentTime(10);
  command.set_type(proto::Command::START_NATIVE_HEAP_SAMPLE);
  command.mutable_start_native_sample()
      ->mutable_configuration()
      ->mutable_perfetto_options();
  auto* manager = SessionsManager::Instance();
  StartNativeSample::Create(command, &trace_manager, manager)
      ->ExecuteOn(daemon_.get());
  {
    std::unique_lock<std::mutex> lock(mutex);
    // Expect that we receive events before the timeout.
    // We should expect a session, status, start.
    EXPECT_TRUE(cv_.wait_for(lock, std::chrono::milliseconds(1000),
                            [this] { return events_.size() == 3; }));
  }

  // event 0 is the Session we can skip it.
  EXPECT_EQ(events_[1].kind(), proto::Event::TRACE_STATUS);
  EXPECT_TRUE(events_[1].has_trace_status());
  EXPECT_TRUE(events_[1].trace_status().has_trace_start_status());
  EXPECT_EQ(events_[1].trace_status().trace_start_status().status(),
            TraceStartStatus::SUCCESS);
  EXPECT_EQ(events_[1].trace_status().trace_start_status().start_time_ns(), 10);
  EXPECT_EQ(events_[1].trace_status().trace_start_status().error_message(), "");

  EXPECT_EQ(events_[2].kind(), proto::Event::MEMORY_TRACE);
  EXPECT_TRUE(events_[2].has_trace_data());
  EXPECT_TRUE(events_[2].trace_data().has_trace_started());
  EXPECT_EQ(
      events_[2].trace_data().trace_started().trace_info().from_timestamp(),
      10);
  EXPECT_EQ(events_[2].trace_data().trace_started().trace_info().to_timestamp(),
            LLONG_MAX);

  // Execute the stop command
  clock_.SetCurrentTime(20);
  command.set_type(proto::Command::STOP_NATIVE_HEAP_SAMPLE);
  command.mutable_stop_native_sample()
      ->mutable_configuration()
      ->mutable_perfetto_options();

  StopNativeSample::Create(command, &trace_manager)->ExecuteOn(daemon_.get());
  {
    std::unique_lock<std::mutex> lock(mutex);
    // Expect that we receive events before the timeout.
    // We should expect a session, status, start and end event
    EXPECT_TRUE(cv_.wait_for(lock, std::chrono::milliseconds(1000),
                            [this] { return events_.size() == 5; }));
  }

  EXPECT_EQ(events_[3].kind(), proto::Event::TRACE_STATUS);
  EXPECT_TRUE(events_[3].has_trace_status());
  EXPECT_TRUE(events_[3].trace_status().has_trace_stop_status());
  EXPECT_EQ(events_[3].trace_status().trace_stop_status().status(),
            TraceStopStatus::SUCCESS);
  EXPECT_EQ(events_[3].trace_status().trace_stop_status().error_message(), "");
  EXPECT_TRUE(events_[3].is_ended());

  EXPECT_EQ(events_[4].kind(), proto::Event::MEMORY_TRACE);
  EXPECT_TRUE(events_[4].has_trace_data());
  EXPECT_TRUE(events_[4].trace_data().has_trace_ended());
  EXPECT_EQ(events_[4].trace_data().trace_ended().trace_info().from_timestamp(),
            10);
  EXPECT_EQ(events_[4].trace_data().trace_ended().trace_info().to_timestamp(),
            20);
  EXPECT_TRUE(events_[4].is_ended());
}

}  // namespace profiler