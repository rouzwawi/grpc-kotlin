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

import io.grpc.Status
import io.grpc.StatusRuntimeException
import kotlinx.coroutines.experimental.CoroutineExceptionHandler
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.produce
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ExceptionPropagationTest : GrpcTestBase() {

    @Rule
    @JvmField
    val expect = ExpectedException.none()

    @Test
    fun unaryStatus() {
        val stub = startServer(StatusThrowingGreeter())

        expect.expect(StatusRuntimeException::class.java)
        expect.expectMessage("NOT_FOUND: neh")

        runBlocking {
            stub.greet(req("joe"))
        }
    }

    @Test
    fun serverStreamingStatus() {
        val stub = startServer(StatusThrowingGreeter())

        expect.expect(StatusRuntimeException::class.java)
        expect.expectMessage("NOT_FOUND: neh")

        runBlocking {
            stub.greetServerStream(req("joe")).receive()
        }
    }

    @Test
    fun clientStreamingStatus() {
        val stub = startServer(StatusThrowingGreeter())

        expect.expect(StatusRuntimeException::class.java)
        expect.expectMessage("NOT_FOUND: neh")

        runBlocking {
            stub.greetClientStream().await()
        }
    }

    @Test
    fun bidirectionalStatus() {
        val stub = startServer(StatusThrowingGreeter())

        expect.expect(StatusRuntimeException::class.java)
        expect.expectMessage("NOT_FOUND: neh")

        runBlocking {
            stub.greetBidirectional().receive()
        }
    }

    @Test
    fun unaryException() {
        val stub = startServer(GenericThrowingGreeter())

        expect.expect(StatusRuntimeException::class.java)
        expect.expectMessage("UNKNOWN")

        runBlocking {
            stub.greet(req("joe"))
        }
    }

    @Test
    fun serverStreamingException() {
        val stub = startServer(GenericThrowingGreeter())

        expect.expect(StatusRuntimeException::class.java)
        expect.expectMessage("UNKNOWN")

        runBlocking {
            stub.greetServerStream(req("joe")).receive()
        }
    }

    @Test
    fun clientStreamingException() {
        val stub = startServer(GenericThrowingGreeter())

        expect.expect(StatusRuntimeException::class.java)
        expect.expectMessage("UNKNOWN")

        runBlocking {
            stub.greetClientStream().await()
        }
    }

    @Test
    fun bidirectionalException() {
        val stub = startServer(GenericThrowingGreeter())

        expect.expect(StatusRuntimeException::class.java)
        expect.expectMessage("UNKNOWN")

        runBlocking {
            stub.greetBidirectional().receive()
        }
    }

    private class StatusThrowingGreeter : GreeterGrpcKt.GreeterImplBase() {

        override suspend fun greet(request: GreetRequest): GreetReply {
            throw notFound()
        }

        override fun greetServerStream(request: GreetRequest) = produce<GreetReply> {
            throw notFound()
        }

        override suspend fun greetClientStream(requestChannel: ReceiveChannel<GreetRequest>): GreetReply {
            throw notFound()
        }

        override fun greetBidirectional(requestChannel: ReceiveChannel<GreetRequest>) = produce<GreetReply> {
            throw notFound()
        }

        private fun notFound(): StatusRuntimeException {
            return Status.NOT_FOUND.withDescription("neh").asRuntimeException()
        }
    }

    private class GenericThrowingGreeter : GreeterGrpcKt.GreeterImplBase(
        Dispatchers.Default + CoroutineExceptionHandler { _, _ -> /* shh */ }
    ) {

        override suspend fun greet(request: GreetRequest): GreetReply {
            throw broke()
        }

        override fun greetServerStream(request: GreetRequest) = produce<GreetReply> {
            throw broke()
        }

        override suspend fun greetClientStream(requestChannel: ReceiveChannel<GreetRequest>): GreetReply {
            throw broke()
        }

        override fun greetBidirectional(requestChannel: ReceiveChannel<GreetRequest>) = produce<GreetReply> {
            throw broke()
        }

        private fun broke(): Exception {
            return Exception("my app broke")
        }
    }

}
