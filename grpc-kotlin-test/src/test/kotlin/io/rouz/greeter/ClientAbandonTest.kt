/*-
 * -\-\-
 * grpc-kotlin-test
 * --
 * Copyright (C) 2016 - 2019 rouz.io
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

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.netty.NettyServerBuilder
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import mu.KotlinLogging
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


@RunWith(JUnit4::class)
class ClientAbandonTest {
    private val log = KotlinLogging.logger {}

    private val svc = InfiniteStreamGreeterImpl()

    private fun server() =
            NettyServerBuilder.forPort(16565)
                    .addService(svc)
                    .build()
                    .start()

    private fun getChannel() = ManagedChannelBuilder
            .forAddress("localhost", 16565)
            .usePlaintext()
            .build().also {
                channels.add(it)
            }


    private fun dumpSubscribers() =
            log.info("Current subscribers: {}", svc.subscribers.keys)

    private val serv = server()
    private val channels: MutableList<ManagedChannel> = mutableListOf()

    @After
    fun tearDown() {
        channels.forEach { ch ->
            if (!ch.shutdownNow().awaitTermination(1, SECONDS)) {
                error("Failed to shutdown channel")
            }
        }
        if (!serv.shutdownNow().awaitTermination(1, SECONDS)) {
            error("Failed to shutdown server")
        }
    }

    @Test
    fun infiniteServerStreamingToSubscribers() {
        // Create two subscribers & subscribe them
        val rouzStub = GreeterGrpc.newStub(getChannel())
        val igorStub = GreeterGrpc.newStub(getChannel())

        val rouzCh = rouzStub.greetServerStream(req("rouz"))
        val igorCh = igorStub.greetServerStream(req("igor"))
        svc.startupSync.acquire(2)

        runBlocking {
            dumpSubscribers()
            assertEquals(setOf("rouz", "igor"), svc.subscribers.keys)

            // Publish greeting to all
            val rouzJob = launch {
                rouzCh.receive().also {
                    assertEquals("Hello rouz", it.reply)
                }
            }

            val igorJob = launch {
                igorCh.receive().also {
                    assertEquals("Hello igor", it.reply)
                }
            }

            val sendJob = launch {
                svc.greetAllSubscribers("Hello")
            }

            joinAll(rouzJob, igorJob, sendJob)
            assertEquals(setOf("rouz", "igor"), svc.subscribers.keys)

            // One client disconnects
            suspendCoroutine<Unit> {
                val ch = igorStub.channel as ManagedChannel
                ch.shutdownNow()
                it.resume(assertTrue(ch.awaitTermination(1, SECONDS)))
            }

            // Continue publishing of new greetings
            val rouzJob2 = launch {
                repeat(4) {
                    assertEquals("Hola $it rouz", rouzCh.receive().reply)
                }
            }

            // One of the first invocations should close the channel of Igor, the next one should remove subscription.
            val senderJob2 = launch {
                repeat(4) {
                    svc.greetAllSubscribers("Hola $it")
                    yield()
                }
            }

            joinAll(rouzJob2, senderJob2)

            // Check that subscription of Igor has gone
            dumpSubscribers()
            assertEquals(setOf("rouz"), svc.subscribers.keys)
        }
    }
}
