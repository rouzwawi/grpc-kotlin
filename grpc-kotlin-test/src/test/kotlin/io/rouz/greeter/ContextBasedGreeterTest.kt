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

import io.grpc.Context
import io.grpc.Contexts
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.stub.MetadataUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.channels.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test

class ContextBasedGreeterTest : GrpcTestBase() {

    companion object {
        private val userContextKey = Context.key<String>("user name")
        private val userMetadataKey = Metadata.Key.of("User-Name", Metadata.ASCII_STRING_MARSHALLER)
    }

    @Test
    fun contextValueMissing() {
        val stub = startServer(ContextGreeter())

        runBlocking {
            val reply = stub.greet(req("rouz"))

            Assert.assertEquals("Hello anonymous", reply.reply)
        }
    }

    @Test
    fun contextValuePresent() {
        val stub = MetadataUtils.attachHeaders(
            startServer(ContextGreeter()),
            Metadata().apply { put(userMetadataKey, "Chad") }
        )

        runBlocking {
            val reply = stub.greet(req("rouz"))

            Assert.assertEquals("Hello Chad", reply.reply)
        }
    }

    @Test
    fun contextTransferredAcrossThreads() {
        /*
         * This test is intended to ensure that the context gets transferred as expected to child coroutines and multiple
         * threads. We ensure this by having the service include the thread ID in the reply and verifying that more than
         * one thread issued replies.
         */
        val stub = MetadataUtils.attachHeaders(
            startServer(ContextGreeter()),
            Metadata().apply { put(userMetadataKey, "Jordan") }
        )

        runBlocking {
            val replies = stub.greetServerStream(req("rouz")).toList()

            val regex = Regex("Hi #\\d{1,3} Jordan from (\\d+)")
            val threadIds = replies
                .map { regex.matchEntire(it.reply) }
                .onEach { Assert.assertNotNull("Reply did not match '${regex.pattern}'", it) }.filterNotNull()
                .map { it.groups[1]?.value }
                .onEach { Assert.assertNotNull(it) }.filterNotNull()
                .toSet()
            Assert.assertNotEquals("All messages were dispatched by a single thread", 1, threadIds.size)
        }
    }

    override fun serverInterceptor(): ServerInterceptor = ServerNameInterceptor

    inner class ContextGreeter : GreeterImplBase() {
        override suspend fun greet(request: GreetRequest): GreetReply =
            repl("Hello ${userContextKey.get() ?: "anonymous"}")

        @ExperimentalCoroutinesApi
        override suspend fun greetServerStream(request: GreetRequest): ReceiveChannel<GreetReply> = produce {
            for (i in 0..99) {
                launch {
                    send(repl("Hi #$i ${userContextKey.get()} from ${Thread.currentThread().id}"))
                }
            }
        }
    }

    object ServerNameInterceptor : ServerInterceptor {
        override fun <ReqT : Any?, RespT : Any?> interceptCall(
            call: ServerCall<ReqT, RespT>,
            headers: Metadata,
            next: ServerCallHandler<ReqT, RespT>
        ): ServerCall.Listener<ReqT> = headers[userMetadataKey]
            ?.let {
                Contexts.interceptCall(
                    Context.current().withValue(userContextKey, it),
                    call,
                    headers,
                    next
                )
            } ?: next.startCall(call, headers)
    }
}
