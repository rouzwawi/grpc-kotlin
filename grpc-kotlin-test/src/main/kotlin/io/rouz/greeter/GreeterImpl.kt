/*-
 * -\-\-
 * grpc-kotlin-test
 * --
 * Copyright (C) 2016 - 2018 rouz.io
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

import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.produce
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.newFixedThreadPoolContext
import mu.KotlinLogging

/**
 * Implementation of coroutine-based gRPC service defined in greeter.proto
 */
class GreeterImpl : GreeterGrpcKt.GreeterImplBase(
    coroutineContext = newFixedThreadPoolContext(4, "server-pool")
) {

    private val log = KotlinLogging.logger("server")

    override suspend fun greet(request: GreetRequest): GreetReply {
        log.info(request.greeting)

        return GreetReply.newBuilder()
            .setReply("Hello " + request.greeting)
            .build()
    }

    override suspend fun greetServerStream(request: GreetRequest) = produce<GreetReply> {
        log.info(request.greeting)

        send(
            GreetReply.newBuilder()
                .setReply("Hello ${request.greeting}!")
                .build()
        )
        send(
            GreetReply.newBuilder()
                .setReply("Greetings ${request.greeting}!")
                .build()
        )
    }

    override suspend fun greetClientStream(requestChannel: ReceiveChannel<GreetRequest>): GreetReply {
        val greetings = mutableListOf<String>()

        for (request in requestChannel) {
            log.info(request.greeting)
            greetings.add(request.greeting)
        }

        return GreetReply.newBuilder()
            .setReply("Hi to all of $greetings!")
            .build()
    }

    override suspend fun greetBidirectional(requestChannel: ReceiveChannel<GreetRequest>) = produce<GreetReply> {
        var count = 0

        for (request in requestChannel) {
            val n = count++
            log.info("$n ${request.greeting}")
            launch {
                delay(1000)
                send(
                    GreetReply.newBuilder()
                        .setReply("Yo #$n ${request.greeting}")
                        .build()
                )
                log.info("dispatched $n")
            }
        }

        log.info("completing")
    }
}
