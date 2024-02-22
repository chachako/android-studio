/*
 * Copyright (C) 2018 The Android Open Source Project
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
#include <gtest/gtest.h>
#include <vector>

#include "daemon/daemon.h"
#include "daemon/event_buffer.h"
#include "perfd/samplers/sampler.h"
#include "perfd/sessions/session.h"
#include "proto/agent_service.grpc.pb.h"
#include "proto/profiler.grpc.pb.h"
#include "utils/clock.h"
#include "utils/count_down_latch.h"
#include "utils/daemon_config.h"
#include "utils/fake_clock.h"
#include "utils/file_cache.h"
#include "utils/fs/memory_file_system.h"

namespace profiler {

class FakeSampler final : public Sampler {
 public:
  FakeSampler(const Session& session, FakeClock* clock, EventBuffer* buffer,
              int64_t sampling_interval_ms, int64_t id, int32_t event_count)
      : Sampler(session, clock, buffer, sampling_interval_ms),
        clock_(clock),
        sampling_interval_ms_(sampling_interval_ms),
        event_count_(event_count),
        group_id_(id),
        latch_(event_count) {}

  virtual void Sample() override {
    if (latch_.count() == 0) {
      return;
    }

    proto::Event event;
    event.set_pid(session().info().pid());
    event.set_group_id(group_id_);
    buffer()->Add(event);
    latch_.CountDown();
  }

  void WaitForSampleCompletion() const {
    // Wait for the latch to reach zero, but at the same time,
    // we need to make the clock move forward, otherwise the samplers
    // will be forever waiting for the sampling interval to finish.
    while (latch_.count() != 0) {
      clock_->Elapse(Clock::ms_to_ns(1));
      usleep(1000);
    }
  }
  int32_t event_count() const { return event_count_; }
  int64_t group_id() const { return group_id_; }

 private:
  virtual const char* name() override { return "FakeSampler"; }

  FakeClock* clock_;
  int64_t sampling_interval_ms_;
  int32_t event_count_;
  int64_t group_id_;
  CountDownLatch latch_;
};

TEST(FakeSampler, TestSamplerInsertion) {
  FakeClock clock;
  DaemonConfig config(proto::DaemonConfig::default_instance());
  FileCache file_cache(std::unique_ptr<FileSystem>(new MemoryFileSystem()),
                       "/");
  EventBuffer event_buffer(&clock);

  Daemon daemon(&clock, &config, &file_cache, &event_buffer);
  Session session(0, 0, 0, &daemon);
  std::vector<FakeSampler*> samplers;
  int64_t sampling_interval_ms = 100;
  for (int i = 1; i < 11; i++) {
    auto sampler = new FakeSampler(session, &clock, &event_buffer,
                                   sampling_interval_ms, i, i);
    samplers.push_back(sampler);
    sampler->Start();
  }

  // Wait for all FakeSamplers to finish
  // Then validate that the event buffer contains all the expected events.
  for (auto sampler : samplers) {
    sampler->WaitForSampleCompletion();

    // Stop the sampler, otherwise we would be deleting it while the sampler
    // threads are still running, which introduces flakiness.
    sampler->Stop();

    proto::EventGroup group;
    ASSERT_TRUE(event_buffer.GetGroup(sampler->group_id(), &group));
    ASSERT_EQ(sampler->event_count(), group.events_size());

    delete sampler;
  }
}

}  // namespace profiler