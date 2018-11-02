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

import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.testing.GrpcCleanupRule
import io.rouz.greeter.GreeterGrpcKt.GreeterImplBase
import io.rouz.greeter.GreeterGrpcKt.GreeterKtStub
import kotlinx.coroutines.CoroutineExceptionHandler
import mu.KotlinLogging
import org.junit.Rule

open class GrpcTestBase {

    val log = KotlinLogging.logger("GreeterImplBase")

    @Rule
    @JvmField
    val grpcCleanup = GrpcCleanupRule()

    private val serverName = InProcessServerBuilder.generateName()

    protected val seenExceptions = mutableListOf<Throwable>()
    protected val collectExceptions = CoroutineExceptionHandler { _, t ->
        seenExceptions += t
        log.info("Caught exception in exception handler: $t")
    }

    protected fun startServer(service: GreeterImplBase): GreeterKtStub {
        grpcCleanup.register(
            InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(service)
                .build()
                .start()
        )

        val channel = grpcCleanup.register(
            InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build()
        )

        return GreeterGrpcKt.newStub(channel)
    }

    fun req(greeting: String): GreetRequest {
        return GreetRequest.newBuilder().setGreeting(greeting).build()
    }
}
