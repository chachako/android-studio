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
package com.studiogrpc.testutils

import com.android.tools.idea.io.grpc.CallOptions
import com.android.tools.idea.io.grpc.Channel
import com.android.tools.idea.io.grpc.ClientInterceptor
import com.android.tools.idea.io.grpc.ForwardingClientCall
import com.android.tools.idea.io.grpc.MethodDescriptor

/** A no-op interceptor. */
object ForwardingInterceptor : ClientInterceptor {

  override fun <ReqT, RespT> interceptCall(
    descriptor: MethodDescriptor<ReqT, RespT>,
    options: CallOptions,
    channel: Channel
  ) =
    object :
      ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
        channel.newCall(descriptor, options)
      ) {}
}
