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
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

abstract class StatusExceptionTestBase : GrpcTestBase() {

    @Rule
    @JvmField
    val expect = ExpectedException.none()

    abstract val service: GreeterGrpcKt.GreeterImplBase

    @Test
    fun unaryStatus() {
        val stub = startServer(service)

        expect.expect(StatusRuntimeException::class.java)
        expect.expectMessage("NOT_FOUND: uni")

        runBlocking {
            stub.greet(req("joe"))
        }
    }

    @Test
    fun serverStreamingStatus() {
        val stub = startServer(service)

        expect.expect(StatusRuntimeException::class.java)
        expect.expectMessage("NOT_FOUND: sstream")

        runBlocking {
            stub.greetServerStream(req("joe")).receive()
        }
    }

    @Test
    fun clientStreamingStatus() {
        val stub = startServer(service)

        expect.expect(StatusRuntimeException::class.java)
        expect.expectMessage("NOT_FOUND: cstream")

        runBlocking {
            stub.greetClientStream().await()
        }
    }

    @Test
    fun bidirectionalStatus() {
        val stub = startServer(service)

        expect.expect(StatusRuntimeException::class.java)
        expect.expectMessage("NOT_FOUND: bidi")

        runBlocking {
            stub.greetBidirectional().receive()
        }
    }

    protected fun notFound(description: String): StatusRuntimeException {
        return Status.NOT_FOUND.withDescription(description).asRuntimeException()
    }
}
