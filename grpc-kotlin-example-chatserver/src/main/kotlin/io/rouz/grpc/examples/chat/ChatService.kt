/*-
 * -\-\-
 * simple-kotlin-standalone-example
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

package io.rouz.grpc.examples.chat

import com.google.protobuf.Empty
import com.google.protobuf.Timestamp
import io.grpc.Status
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.concurrent.ConcurrentSkipListMap

@UseExperimental(ExperimentalCoroutinesApi::class)
class ChatService : ChatServiceImplBase() {
    private val clientChannels = ConcurrentSkipListMap<String, SendChannel<ChatMessageFromService>>()

    override suspend fun getNames(request: Empty): ChatRoom {
        return ChatRoom.newBuilder()
                .addAllNames(clientChannels.keys)
                .build()
    }

    private fun createChannel() = Channel<ChatMessageFromService>(100).apply {
        invokeOnClose {
            it?.printStackTrace()
        }
    }

    private fun subscribe(name: String, ch: SendChannel<ChatMessageFromService>) {
        println("New client connected: $name")
        clientChannels.put(name, ch)
                ?.apply {
                    println("Close duplicate channel of user: $name")
                    close()
                }
    }

    private suspend fun broadcast(message: ChatMessage) = createMessage(message)
            .let { broadcastMessage ->
                println("Broadcast ${message.from}: ${message.message}")

                clientChannels.asSequence()
                        .filterNot { (name, _) -> name == message.from }
                        .forEach { (other, ch) ->
                            launch {
                                try {
                                    println("Sending to $other")
                                    ch.send(broadcastMessage)
                                } catch (e: Throwable) {
                                    println("$other hung up: ${e.message}. Removing client channel")
                                    clientChannels.remove(other)?.close(e)
                                }
                            }
                        }
            }


    override fun chat(requests: ReceiveChannel<ChatMessage>): ReceiveChannel<ChatMessageFromService> =
            createChannel().also {
                GlobalScope.launch {
                    doChat(requests, it)
                }
            }

    private suspend fun doChat(req: ReceiveChannel<ChatMessage>, resp: SendChannel<ChatMessageFromService>) {
        val hello = req.receive()
        subscribe(hello.from, resp)
        broadcast(hello)

        try {
            for (chatMessage in req) {
                println("Got request from $req:")
                println(chatMessage)
                broadcast(chatMessage)
            }
        } catch (t: Throwable) {
            println("Threw $t")
            if (Status.fromThrowable(t).code != Status.Code.CANCELLED) {
                println("An actual error occurred")
                t.printStackTrace()
            }
        } finally {
            println("${hello.from} hung up. Removing client channel")
            clientChannels.remove(hello.from)
            if (!resp.isClosedForSend) {
                resp.close()
            }
        }
    }

    override suspend fun say(request: ChatMessage): Empty = Empty.getDefaultInstance().also {
        broadcast(request)
    }

    override fun listen(request: WhoAmI): ReceiveChannel<ChatMessageFromService> = createChannel().also {
        subscribe(request.name, it)
    }

    fun shutdown() {
        println("Shutting down Chat service")
        clientChannels.forEach { (client, channel) ->
            println("Closing client channel $client")
            channel.close()
        }
        clientChannels.clear()
    }

    private fun createMessage(request: ChatMessage) = ChatMessageFromService.newBuilder()
            .run {
                timestamp = Instant.now().run {
                    Timestamp.newBuilder().run {
                        seconds = epochSecond
                        nanos = nano
                        build()
                    }
                }
                message = request
                build()
            }

}
