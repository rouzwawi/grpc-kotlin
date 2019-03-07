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

import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch

fun main(args: Array<String>) {
    chatClient()
}

fun chatClient() {
    val channel = ManagedChannelBuilder.forAddress("localhost", 15001)
        .usePlaintext()
        .build()

    val chatService = ChatServiceGrpc.newStub(channel)

    val chat = chatService.chat()

    println("type :q to quit")
    print("Enter your name: ")
    val from = readLine()
    chat.send(
        ChatMessage.newBuilder()
            .setFrom(from)
            .build()
    )

    startPrintLoop(chat)

    try {
        while (true) {
            print("Message: ")
            val message = readLine()
            if (message == null || message == ":q") {
                break
            }
            chat.send(
                ChatMessage.newBuilder()
                    .setFrom(from)
                    .setMessage(message)
                    .build()
            )
        }
    } finally {
        println("closing")
        chat.close()
    }
}

private fun startPrintLoop(chat: ReceiveChannel<ChatMessageFromService>) = GlobalScope.launch {
    try {
        for (responseMessage in chat) {
            val message = responseMessage.message
            println("${message.from}: ${message.message}")
        }
        println("Server disconnected")
    } catch (e: Throwable) {
        println("Server disconnected badly: $e")
    }
}
