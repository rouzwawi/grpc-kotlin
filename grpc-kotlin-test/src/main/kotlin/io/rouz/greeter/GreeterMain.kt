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

import io.grpc.ManagedChannelBuilder
import io.grpc.ServerBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging

fun main(args: Array<String>) {
    val log = KotlinLogging.logger("client")
    ServerBuilder.forPort(8080)
        .addService(GreeterImpl())
        .build()
        .start()
    val localhost = ManagedChannelBuilder.forAddress("localhost", 8080)
        .usePlaintext()
        .build()

    val greeter = GreeterGrpcKt.newStub(localhost)

    runBlocking {
        // === Unary call =============================================================================

        val unaryResponse = greeter.greet(req("Alice"))
        log.info("unary reply = ${unaryResponse.reply}")

        // === Server streaming call ==================================================================

        val serverResponses = greeter.greetServerStream(req("Bob"))
        for (serverResponse in serverResponses) {
            log.info("server response = ${serverResponse.reply}")
        }

        // === Client streaming call ==================================================================

        val manyToOneCall = greeter.greetClientStream()
        manyToOneCall.send(req("Caroline"))
        manyToOneCall.send(req("David"))
        manyToOneCall.close()
        val oneReply = manyToOneCall.await()
        log.info("single reply = ${oneReply.reply}")

        // === Bidirectional call =====================================================================

        val bidiCall = greeter.greetBidirectional()
        launch {
            var n = 0
            for (greetReply in bidiCall) {
                log.info("reply $n = ${greetReply.reply}")
                n++
            }
            log.info("no more replies")
        }

        delay(200)
        bidiCall.send(req("Eve"))

        delay(200)
        bidiCall.send(req("Fred"))

        delay(200)
        bidiCall.send(req("Gina"))
        bidiCall.close()
    }
}

fun req(greeting: String): GreetRequest {
    return GreetRequest.newBuilder().setGreeting(greeting).build()
}
