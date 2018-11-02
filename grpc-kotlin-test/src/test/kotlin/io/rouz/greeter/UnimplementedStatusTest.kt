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
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

class UnimplementedStatusTest : GrpcTestBase() {

    @Rule
    @JvmField
    val expect = ExpectedException.none()

    @Test
    fun unaryUnimplemented() {
        val stub = startServer(UnimplementedGreeter())

        expect.expect(StatusRuntimeException::class.java)
        expect.expectMessage("UNIMPLEMENTED: Method io.rouz.greeter.Greeter/Greet is unimplemented")

        runBlocking {
            stub.greet(req("anyone there?"))
        }
    }

    @Test
    fun clientStreamingUnimplemented() {
        val stub = startServer(UnimplementedGreeter())

        expect.expect(StatusRuntimeException::class.java)
        expect.expectMessage("UNIMPLEMENTED: Method io.rouz.greeter.Greeter/GreetClientStream is unimplemented")

        runBlocking {
            stub.greetClientStream().await()
        }
    }

    @Test
    fun serverStreamingUnimplemented() {
        val stub = startServer(UnimplementedGreeter())

        expect.expect(StatusRuntimeException::class.java)
        expect.expectMessage("UNIMPLEMENTED: Method io.rouz.greeter.Greeter/GreetServerStream is unimplemented")

        runBlocking {
            stub.greetServerStream(req("anyone there?")).receive()
        }
    }

    @Test
    fun bidirectionalUnimplemented() {
        val stub = startServer(UnimplementedGreeter())

        expect.expect(StatusRuntimeException::class.java)
        expect.expectMessage("UNIMPLEMENTED: Method io.rouz.greeter.Greeter/GreetBidirectional is unimplemented")

        runBlocking {
            stub.greetBidirectional().receive()
        }
    }

    inner class UnimplementedGreeter : GreeterGrpcKt.GreeterImplBase(collectExceptions)
}
