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

import io.grpc.ClientInterceptor
import io.grpc.ClientInterceptors
import io.grpc.ServerInterceptor
import io.grpc.ServerInterceptors
import io.grpc.testing.GrpcServerRule
import io.rouz.greeter.GreeterGrpc.GreeterStub
import kotlinx.coroutines.CoroutineExceptionHandler
import mu.KotlinLogging
import org.junit.Rule

open class GrpcTestBase {

    val log = KotlinLogging.logger("GreeterImplBase")

    @Rule
    @JvmField
    val grpcServer: GrpcServerRule = GrpcServerRule().directExecutor()

    protected val seenExceptions = mutableListOf<Throwable>()
    protected val collectExceptions = CoroutineExceptionHandler { _, t ->
        seenExceptions += t
        log.info("Caught exception in exception handler: $t")
    }

    protected fun startServer(service: GreeterImplBase): GreeterStub {
        val serviceDefinition = serverInterceptor()?.let {
            ServerInterceptors.intercept(service, it)
        } ?: service.bindService()
        grpcServer.serviceRegistry.addService(serviceDefinition)

        val channel = clientInterceptor()?.let {
            ClientInterceptors.intercept(grpcServer.channel, it)
        } ?: grpcServer.channel

        return GreeterGrpc.newStub(channel)
    }

    protected open fun serverInterceptor(): ServerInterceptor? = null

    protected open fun clientInterceptor(): ClientInterceptor? = null

    fun req(greeting: String): GreetRequest {
        return GreetRequest.newBuilder().setGreeting(greeting).build()
    }

    fun repl(reply: String): GreetReply {
        return GreetReply.newBuilder().setReply(reply).build()
    }
}
