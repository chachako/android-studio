/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.lint.checks.infrastructure

/**
 * A [MultiRun] is a task which lets you [run] your already configured `lint` task repeatedly,
 * applying multiple different configurations such as command line flags, and then perform either
 * the same verification step on all the test results, or individual verification steps for each
 * test run.
 */
class MultiRun internal constructor(private val root: TestLintTask) {
  class Step(val setup: TestLintTask.() -> Any?, val verify: TestLintResult.() -> Any?)

  /**
   * Run the lint tests repeatedly, performing additional [TestLintTask] customizations for each run
   * and then running verification steps (such as [TestLintResult.expect]) on the results. This
   * method specifies a single verification step, meant to be the same for all the variations. If
   * you expect each test run to potentially have different expectations, construct individual
   * [Step] objects which maps setup to individual tasks instead.
   */
  fun run(vararg configurations: TestLintTask.() -> Any?, verify: TestLintResult.() -> Any) {
    val steps = configurations.map { Step(setup = it, verify = verify) }
    run(*steps.toTypedArray())
  }

  /** Run the configured task repeatedly. */
  fun run(vararg configurations: Step) {
    val taskToVerifyMap = mutableListOf<Pair<TestLintTask, TestLintResult.() -> Any?>>()

    for (configuration in configurations) {
      val setup = configuration.setup
      val expect = configuration.verify
      val copy: TestLintTask = root.copy()
      setup.invoke(copy)
      taskToVerifyMap.add(copy to expect)
    }
    for ((task, verify) in taskToVerifyMap) {
      val result = task.run()
      verify(result)
    }
  }
}
