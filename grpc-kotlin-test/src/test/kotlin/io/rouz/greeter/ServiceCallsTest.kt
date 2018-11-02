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

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ServiceCallsTest : GrpcTestBase() {

    @Test
    fun unaryGreet() {
        val stub = startServer(GreeterImpl())

        runBlocking {
            val reply = stub.greet(req("rouz"))

            assertEquals("Hello rouz", reply.reply)
        }
    }

    @Test
    fun serverStreamingGreet() {
        val stub = startServer(GreeterImpl())

        runBlocking {
            val replies = stub.greetServerStream(req("rouz"))

            assertEquals("Hello rouz!", replies.receive().reply)
            assertEquals("Greetings rouz!", replies.receive().reply)
        }
    }

    @Test
    fun clientStreamingGreet() {
        val stub = startServer(GreeterImpl())

        runBlocking {
            val call = stub.greetClientStream()

            call.send(req("rouz"))
            call.send(req("delavari"))
            call.close()

            val reply = call.await()

            assertEquals("Hi to all of [rouz, delavari]!", reply.reply)
        }
    }

    @Test
    fun bidirectionalGreet() {
        val stub = startServer(GreeterImpl())

        runBlocking {
            val call = stub.greetBidirectional()

            call.send(req("rouz"))
            val reply1 = call.receive()
            assertEquals("Yo #0 rouz", reply1.reply)

            call.send(req("delavari"))
            val reply2 = call.receive()
            assertEquals("Yo #1 delavari", reply2.reply)

            call.close()
        }
    }
}
