/*-
 * -\-\-
 * grpc-kotlin-test
 * --
 * Copyright (C) 2016 - 2019 rouz.io
 * --
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
 * -/-/-
 */


package io.rouz.greeter

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore

class InfiniteStreamGreeterImpl : GreeterImpl() {

    val subscribers = ConcurrentHashMap<String, Channel<GreetReply>>()
    val startupSync = Semaphore(0)

    override fun greetServerStream(request: GreetRequest): ReceiveChannel<GreetReply> =
            Channel<GreetReply>(100).also {
                val name = request.greeting
                subscribers[name] = it
                log.info("Subscribed: {}", name)
                startupSync.release()
            }

    suspend fun greetAllSubscribers(word: String) {
        withContext(Dispatchers.IO) {
            subscribers.forEach { (subs, ch) ->
                GreetReply.newBuilder().run {
                    reply = "$word $subs"
                    build()
                }.also {
                    launch {
                        try {
                            ch.send(it)
                            log.info("Message to client: {}", it.reply)
                        } catch (e: Throwable) {
                            log.info("Unsubscribe client: {}", subs)
                            subscribers.remove(subs)
                            ch.close()
                        }
                    }
                }
            }
        }
    }
}
