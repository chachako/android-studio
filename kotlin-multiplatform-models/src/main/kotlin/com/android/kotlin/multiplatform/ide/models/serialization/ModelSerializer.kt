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

package com.android.kotlin.multiplatform.ide.models.serialization

import com.android.kotlin.multiplatform.models.AndroidTarget
import com.google.protobuf.InvalidProtocolBufferException
import org.jetbrains.kotlin.gradle.idea.serialize.IdeaKotlinExtrasSerializer
import org.jetbrains.kotlin.gradle.idea.serialize.IdeaKotlinSerializationContext
import org.jetbrains.kotlin.gradle.idea.serialize.IdeaKotlinSerializationLogger

private fun IdeaKotlinSerializationLogger.handleException(
    modelName: String,
    e: Exception
) {
    error("Failed to deserialize $modelName.", e)
}

/**
 * An object that serializes and deserializes android target models.
 */
object AndroidTargetModelSerializer: IdeaKotlinExtrasSerializer<() -> AndroidTarget?> {

    override fun deserialize(
        context: IdeaKotlinSerializationContext,
        data: ByteArray
    ): () -> AndroidTarget? = with(context.logger) {
        {
            try {
                AndroidTarget.parseFrom(data)
            } catch (e: InvalidProtocolBufferException) {
                handleException("Android Target Model", e)
                null
            }
        }
    }

    override fun serialize(
        context: IdeaKotlinSerializationContext,
        value: () -> AndroidTarget?
    ): ByteArray? = value()?.toByteArray()
}
