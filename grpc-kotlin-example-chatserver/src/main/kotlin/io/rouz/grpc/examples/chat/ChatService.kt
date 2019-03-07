/*-
 * -\-\-
 * simple-kotlin-standalone-example
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

package io.rouz.grpc.examples.chat

import com.google.protobuf.Empty
import com.google.protobuf.Timestamp
import io.grpc.Status
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch

@UseExperimental(ExperimentalCoroutinesApi::class)
class ChatService : ChatServiceImplBase() {

    data class Client(val name: String, val channel: SendChannel<ChatMessageFromService>)

    private val clientChannels = LinkedHashSet<Client>()

    override suspend fun getNames(request: Empty): ChatRoom {
        return ChatRoom.newBuilder()
            .addAllNames(clientChannels.map(Client::name))
            .build()
    }

    override suspend fun chat(requests: ReceiveChannel<ChatMessage>): ReceiveChannel<ChatMessageFromService> {
        val channel = Channel<ChatMessageFromService>(Channel.UNLIMITED)
        println("New client connection: $channel")

        // wait for first message
        val hello = requests.receive()
        val name = hello.from
        val client = Client(name, channel)
        clientChannels.add(client)
        channel.invokeOnClose {
            it?.printStackTrace()
        }

        launch {
            try {
                for (chatMessage in requests) {
                    println("Got request from $requests:")
                    println(chatMessage)
                    val message = createMessage(chatMessage)
                    clientChannels
                        .filter { it.name != chatMessage.from }
                        .forEach { other ->
                            println("Sending to $other")
                            other.channel.send(message)
                        }
                }
            } catch (t: Throwable) {
                println("Threw $t")
                if (Status.fromThrowable(t).code != Status.Code.CANCELLED) {
                    println("An actual error occurred")
                    t.printStackTrace()
                }
            } finally {
                println("$name hung up. Removing client channel")
                clientChannels.remove(client)
                if (!channel.isClosedForSend) {
                    channel.close()
                }
            }
        }

        return channel
    }

    fun shutdown() {
        println("Shutting down Chat service")
        clientChannels.stream().forEach { client ->
            println("Closing client channel $client")
            client.channel.close()
        }
        clientChannels.clear()
    }

    private fun createMessage(request: ChatMessage): ChatMessageFromService {
        return ChatMessageFromService.newBuilder()
            .setTimestamp(
                Timestamp.newBuilder()
                    .setSeconds(System.nanoTime() / 1000000000)
                    .setNanos((System.nanoTime() % 1000000000).toInt())
                    .build()
            )
            .setMessage(request)
            .build()
    }
}
