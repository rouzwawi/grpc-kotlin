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

import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.ReceiveChannel

class ClosingStatusExceptionTest : StatusExceptionTestBase() {

    override val service: GreeterGrpcKt.GreeterImplBase
        get() = StatusThrowingGreeter()

    private inner class StatusThrowingGreeter : GreeterGrpcKt.GreeterImplBase(collectExceptions) {

        override suspend fun greet(request: GreetRequest): GreetReply {
            throw notFound("uni")
        }

        override suspend fun ProducerScope<GreetReply>.greetServerStream(request: GreetRequest) {
            close(notFound("sstream"))
        }

        override suspend fun greetClientStream(requestChannel: ReceiveChannel<GreetRequest>): GreetReply {
            throw notFound("cstream")
        }

        override suspend fun ProducerScope<GreetReply>.greetBidirectional(requestChannel: ReceiveChannel<GreetRequest>) {
            close(notFound("bidi"))
        }
    }
}
