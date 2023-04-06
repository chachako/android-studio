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

package com.google.firebase.testlab.gradle

import org.gradle.api.Incubating

/**
 * A DSL for configuring test execution.
 */
@Incubating
interface Execution {
    /**
     * The maximum time to run the test execution before cancellation
     * measured in minutes. Does not include the setup/teardown of device and
     * is handled server side.
     *
     * The maximum possible testing time is 45 minutes on physical devices
     * and 60 minutes on virtual devices. As specified by FTL.
     *
     * Default value is 15 minutes.
     */
    @get:Incubating
    @set:Incubating
    var timeoutMinutes: Int

    /**
     * Number of times the test should be rerun if tests fail.
     * The number of times a Test Execution should be re-attempted if one
     * or more of its test cases fail.
     *
     * The maximum possible test reruns are 10. Default value is 0.
     */
    @get:Incubating
    @set:Incubating
    var maxTestReruns: Int

    /**
     * Ensures only a single attempt will be made for each execution if
     * an infrastructure issue occurs.
     * This does not affect [maxTestReruns]. Normally, 2 or more attempts
     * are made by FTL if a potential infrastructure issue is detected. This
     * is best enabled for latency sensitive workloads. The # of execution
     * failures may be significantly greater with failFast enabled.
     *
     * Default value is false.
     */
    @get:Incubating
    @set:Incubating
    var failFast: Boolean

    /**
     * Specifies the number of shards across which to distribute test cases. The shards are run
     * in parallel on separate devices through FTL.
     *
     * Only one of [numUniformShards] and [targetedShardDuration] should be set.
     *
     * This is based on the sharding mechanism AndroidJUnitRunner uses, and as such there is no
     * guarantee that test cases will be distributed with perfect uniformity.
     *
     * The number of shards specified must always be a positive number that is no greater than the
     * total number of test cases.
     *
     * For FTL physical devices the number of shards should be <= 50.
     *
     * For FTL virtual devices the number of shards should be <= 100.
     *
     * The Default value is 0, in which case, no uniform sharding will be used.
     */
    @get:Incubating
    @set:Incubating
    var numUniformShards: Int

    /**
     * Enables sharding based on previous tests' timing records. Specifies the amount of time tests
     * within a given shard should take.
     *
     * Only one of [numUniformShards] and [targetedShardDurationMinutes] should be set.
     *
     * When set, the shard count is dynamically set based on time, up to the maximum shard limit
     * (50 for physical devices and 100 for virtual devices). The number of shards will not exceed
     * the number of test cases.
     *
     * The shard duration is not guaranteed because it is based on test result history or default
     * durations if no history exists (15 seconds per test). Shard duration will also be exceeded
     * if:
     *     - The maximum shard limit is reached and there is more calculated test time than what can
     *     be allocated into the shards.
     *     - Any individual test is estimated to be longer than the [targetedShardDurationMinutes]
     *
     * As such, the actual shard duration can exceed the targeted shard duration. We recommend
     * setting the targeted value at least 5 minutes less than [timeoutMinutes] to avoid shards
     * being cancelled before tests can finish.
     *
     * Each shard created will count toward the daily test quota for Firebase Test Lab.
     *
     * The Default value is 0, in which case, smart sharding will not be used.
     */
    @get:Incubating
    @set:Incubating
    var targetedShardDurationMinutes: Int

}
