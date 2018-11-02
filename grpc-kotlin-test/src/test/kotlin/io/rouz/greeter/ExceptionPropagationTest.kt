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

import io.grpc.StatusRuntimeException
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.lang.Thread.sleep

@RunWith(JUnit4::class)
class ExceptionPropagationTest : GrpcTestBase() {

    @Rule
    @JvmField
    val expect = ExpectedException.none()

    @Test
    fun unaryException() {
        val stub = startServer(CustomThrowingGreeter())

        expect.expect(StatusRuntimeException::class.java)
        expect.expectMessage("UNKNOWN")

        runBlocking {
            stub.greet(req("joe"))
        }
    }

    @Test
    fun serverStreamingException() {
        val stub = startServer(CustomThrowingGreeter())

        expect.expect(StatusRuntimeException::class.java)
        expect.expectMessage("UNKNOWN")

        runBlocking {
            stub.greetServerStream(req("joe")).receive()
        }
    }

    @Test
    fun clientStreamingException() {
        val stub = startServer(CustomThrowingGreeter())

        expect.expect(StatusRuntimeException::class.java)
        expect.expectMessage("UNKNOWN")

        runBlocking {
            stub.greetClientStream().await()
        }
    }

    @Test
    fun bidirectionalException() {
        val stub = startServer(CustomThrowingGreeter())

        expect.expect(StatusRuntimeException::class.java)
        expect.expectMessage("UNKNOWN")

        runBlocking {
            stub.greetBidirectional().receive()
        }
    }

    @Test
    fun unaryPropagateCustomException() {
        val stub = startServer(CustomThrowingGreeter())

        expect.expect(CustomThrowingGreeter.CustomException::class.java)
        expect.expectMessage("my app broke uni")

        try {
            runBlocking {
                stub.greet(req("joe"))
            }
        } catch (t: Throwable) { // silence
        }

        sleep(100) // wait for worker threads to invoke exception handler
        throw seenExceptions[0]
    }

    @Test
    fun serverStreamingPropagateCustomException() {
        val stub = startServer(CustomThrowingGreeter())

        expect.expect(CustomThrowingGreeter.CustomException::class.java)
        expect.expectMessage("my app broke sstream")

        try {
            runBlocking {
                stub.greetServerStream(req("jow")).receive()
            }
        } catch (t: Throwable) { // silence
        }

        sleep(100) // wait for worker threads to invoke exception handler
        throw seenExceptions[0]
    }

    @Test
    fun clientStreamingPropagateCustomException() {
        val stub = startServer(CustomThrowingGreeter())

        expect.expect(CustomThrowingGreeter.CustomException::class.java)
        expect.expectMessage("my app broke cstream")

        try {
            runBlocking {
                stub.greetClientStream().await()
            }
        } catch (t: Throwable) { // silence
        }

        sleep(100) // wait for worker threads to invoke exception handler
        throw seenExceptions[0]
    }

    @Test
    fun bidirectionalPropagateCustomException() {
        val stub = startServer(CustomThrowingGreeter())

        expect.expect(CustomThrowingGreeter.CustomException::class.java)
        expect.expectMessage("my app broke bidi")

        try {
            runBlocking {
                stub.greetBidirectional().receive()
            }
        } catch (t: Throwable) { // silence
        }

        sleep(100) // wait for worker threads to invoke exception handler
        throw seenExceptions[0]
    }

    inner class CustomThrowingGreeter : GreeterGrpcKt.GreeterImplBase(collectExceptions) {

        override suspend fun greet(request: GreetRequest): GreetReply {
            throw broke("uni")
        }

        override suspend fun ProducerScope<GreetReply>.greetServerStream(request: GreetRequest) {
            throw broke("sstream")
        }

        override suspend fun greetClientStream(requestChannel: ReceiveChannel<GreetRequest>): GreetReply {
            throw broke("cstream")
        }

        override suspend fun ProducerScope<GreetReply>.greetBidirectional(requestChannel: ReceiveChannel<GreetRequest>) {
            throw broke("bidi")
        }

        private fun broke(description: String): Exception {
            return CustomException("my app broke $description")
        }

        inner class CustomException(message: String) : Exception(message)
    }
}
