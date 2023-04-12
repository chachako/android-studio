/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.fakeadbserver.statechangehubs

/**
 * This class is the base multiplexer for events that need to be propagated to existing
 * client/server connections.
 *
 * @param FactoryType This is the class type of the factory that will create the handlers that
 * this hub will serve.
 */
abstract class StateChangeHub<FactoryType : StateChangeHandlerFactory> {

    @JvmField
    protected val mHandlers: MutableMap<StateChangeQueue, FactoryType> = HashMap()

    @Volatile
    protected var mStopped = false

    /**
     * Cleanly shuts down the hub and closes all existing connections.
     */
    fun stop() {
        synchronized(mHandlers) {
            mStopped = true
            mHandlers.forEach { (stateChangeQueue: StateChangeQueue, changeHandlerFactory: FactoryType) ->
                stateChangeQueue
                    .add { StateChangeHandlerFactory.HandlerResult(false) }
            }
        }
    }

    fun subscribe(handlerFactory: FactoryType): StateChangeQueue? {
        synchronized(mHandlers) {
            if (mStopped) {
                return null
            }
            val queue = StateChangeQueue()
            mHandlers[queue] = handlerFactory
            return queue
        }
    }

    fun unsubscribe(queue: StateChangeQueue) {
        synchronized(mHandlers) {
            assert(mHandlers.containsKey(queue))
            mHandlers.remove(queue)
        }
    }
}
