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
#include "heap_dump.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include "daemon/event_writer.h"
#include "perfd/memory/heap_dump_manager.h"
#include "perfd/sessions/sessions_manager.h"
#include "utils/fake_clock.h"
#include "utils/fs/memory_file_system.h"

#include <condition_variable>
#include <mutex>
#include <thread>
#include <vector>

using std::string;

using ::testing::_;
using ::testing::Return;

using profiler::proto::HeapDumpStatus;

namespace profiler {
class MockActivityManager final : public ActivityManager {
 public:
  explicit MockActivityManager()
      : ActivityManager(
            std::unique_ptr<BashCommandRunner>(new BashCommandRunner("blah"))) {
  }
  MOCK_CONST_METHOD3(TriggerHeapDump,
                     bool(int pid, const std::string& file_path,
                          std::string* error_string));
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

class HeapDumpTest : public testing::Test {
 public:
  HeapDumpTest() : clock_(), event_buffer_(&clock_) {}

  void SetUp() override {
    DaemonConfig config(proto::DaemonConfig::default_instance());
    file_cache_ = std::unique_ptr<FileCache>(new FileCache(
        std::unique_ptr<FileSystem>(new MemoryFileSystem()), "/")),
    daemon_ = std::unique_ptr<Daemon>(
        new Daemon(&clock_, &config, file_cache_.get(), &event_buffer_));

    EXPECT_CALL(activity_manager_, TriggerHeapDump(_, _, _))
        .WillRepeatedly(Return(true));
    dump_ = std::unique_ptr<HeapDumpManager>(
        new HeapDumpManager(file_cache_.get(), &activity_manager_));

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

  // Variables referenced by the test below.
  FakeClock clock_;
  EventBuffer event_buffer_;
  std::unique_ptr<FileCache> file_cache_;
  std::unique_ptr<Daemon> daemon_;
  std::vector<proto::Event> events_;
  std::condition_variable cv_;
  std::unique_ptr<std::thread> read_thread_;
  std::unique_ptr<TestEventWriter> writer_;
  MockActivityManager activity_manager_;
  std::unique_ptr<HeapDumpManager> dump_;
};

// Test that we receive the start and end events for a successful heap dump.
TEST_F(HeapDumpTest, CommandsGeneratesEvents) {
  // Execute the start command
  clock_.SetCurrentTime(10);
  proto::Command command;
  command.set_type(proto::Command::HEAP_DUMP);
  HeapDump::Create(command, dump_.get(), SessionsManager::Instance(), false)
      ->ExecuteOn(daemon_.get());

  std::mutex mutex;
  {
    std::unique_lock<std::mutex> lock(mutex);
    // Expect that we receive events before the timeout.
    // We should expect a status, start and end event
    EXPECT_TRUE(cv_.wait_for(lock, std::chrono::milliseconds(5000),
                             [this] { return events_.size() == 3; }));
  }

  EXPECT_EQ(3, events_.size());
  EXPECT_EQ(events_[0].kind(), proto::Event::MEMORY_HEAP_DUMP_STATUS);
  EXPECT_TRUE(events_[0].has_memory_heapdump_status());
  EXPECT_EQ(events_[0].memory_heapdump_status().status().status(),
            HeapDumpStatus::SUCCESS);
  EXPECT_EQ(events_[0].memory_heapdump_status().status().start_time(), 10);

  EXPECT_EQ(events_[1].kind(), proto::Event::MEMORY_HEAP_DUMP);
  EXPECT_TRUE(events_[1].has_memory_heapdump());
  EXPECT_EQ(events_[1].memory_heapdump().info().start_time(), 10);
  EXPECT_EQ(events_[1].memory_heapdump().info().end_time(), LLONG_MAX);
  EXPECT_FALSE(events_[1].memory_heapdump().info().success());

  EXPECT_EQ(events_[2].kind(), proto::Event::MEMORY_HEAP_DUMP);
  EXPECT_TRUE(events_[2].has_memory_heapdump());
  EXPECT_EQ(events_[2].memory_heapdump().info().start_time(), 10);
  EXPECT_EQ(events_[2].memory_heapdump().info().end_time(), 10);
  // TODO success status from the FileSystemNotifier apis seems
  // platform-dependent in our test scenario. Refactor the logic in
  // heap_dump_manager_test so we can test the O+ workflow here instead.
}

// Test that we receive the start and end events for a successful heap dump
// under the Task-Based UX.
TEST_F(HeapDumpTest, CommandsGeneratesEventsInTaskBasedUX) {
  // Start session so that there is a session to end on heap dump termination.
  auto* manager = SessionsManager::Instance();
  proto::BeginSession begin_session;
  manager->BeginSession(daemon_.get(), 0, 0, begin_session);

  // Execute the start command
  clock_.SetCurrentTime(10);
  proto::Command command;
  command.set_type(proto::Command::HEAP_DUMP);
  HeapDump::Create(command, dump_.get(), SessionsManager::Instance(), true)
      ->ExecuteOn(daemon_.get());
  std::mutex mutex;
  {
    std::unique_lock<std::mutex> lock(mutex);
    // Expect that we receive events before the timeout.
    // We should expect a status, start and end event
    EXPECT_TRUE(cv_.wait_for(lock, std::chrono::seconds(10),
                             [this] { return events_.size() == 5; }));
  }

  EXPECT_EQ(5, events_.size());

  EXPECT_EQ(events_[0].kind(), proto::Event::SESSION);
  EXPECT_TRUE(events_[0].has_session());
  EXPECT_TRUE(events_[0].session().has_session_started());

  EXPECT_EQ(events_[1].kind(), proto::Event::MEMORY_HEAP_DUMP_STATUS);
  EXPECT_TRUE(events_[1].has_memory_heapdump_status());
  EXPECT_EQ(events_[1].memory_heapdump_status().status().status(),
            HeapDumpStatus::SUCCESS);
  EXPECT_EQ(events_[1].memory_heapdump_status().status().start_time(), 10);

  EXPECT_EQ(events_[2].kind(), proto::Event::MEMORY_HEAP_DUMP);
  EXPECT_TRUE(events_[2].has_memory_heapdump());
  EXPECT_EQ(events_[2].memory_heapdump().info().start_time(), 10);
  EXPECT_EQ(events_[2].memory_heapdump().info().end_time(), LLONG_MAX);
  EXPECT_FALSE(events_[2].memory_heapdump().info().success());

  EXPECT_EQ(events_[3].kind(), proto::Event::MEMORY_HEAP_DUMP);
  EXPECT_TRUE(events_[3].has_memory_heapdump());
  EXPECT_EQ(events_[3].memory_heapdump().info().start_time(), 10);
  EXPECT_EQ(events_[3].memory_heapdump().info().end_time(), 10);

  // Expect that a session ended event is present.
  EXPECT_EQ(events_[4].kind(), proto::Event::SESSION);
  EXPECT_TRUE(events_[4].is_ended());
}
}  // namespace profiler
