# gRPC Kotlin - Coroutine based gRPC for Kotlin

[![CircleCI](https://img.shields.io/circleci/project/github/rouzwawi/grpc-kotlin.svg)](https://circleci.com/gh/rouzwawi/grpc-kotlin)
[![Maven Central](https://img.shields.io/maven-central/v/io.rouz/grpc-kotlin-gen.svg)](https://search.maven.org/#search%7Cga%7C1%7Cg%3A%22io.rouz%22%20grpc-kotlin-gen)

gRPC Kotlin is a [protoc] plugin for generating native Kotlin bindings using [coroutine primitives] for [gRPC] services.

## Why?

The asynchronous nature of bidirectional streaming rpc calls in gRPC makes them a bit hard to implement
and read. Getting your head around the `StreamObserver<T>`'s can be a bit tricky at times. Specially
with the method argument being the response observer and the return value being the request observer, it all
feels a bit backwards to what a plain old synchronous version of the handler would look like.

In situations where you'd want to coordinate several request and response messages in one call, you'll and up
having to manage some tricky state and synchronization between the observers. There's some [reactive bindings]
for gRPC which make this easier. But I think we can do better!

Enter Kotlin Coroutines! By generating native Kotlin stubs that allows us to use [`Channel`] and [`Deferred`],
we can write our handler and client code in a much more readable fashion that is a lot easier to reason
about.

## Quick start

note: This has been tested with `gRPC 1.15.1`, `protobuf 3.5.1` and `kotlin 1.2.71`.

Add a gRPC service definition to your project

`greeter.proto`

```proto
syntax = "proto3";
package org.example.greeter;

option java_package = "org.example.greeter";
option java_multiple_files = true;

message GreetRequest {
    string greeting = 1;
}

message GreetReply {
    string reply = 1;
}

service Greeter {
    rpc Greet (GreetRequest) returns (GreetReply);
    rpc GreetServerStream (GreetRequest) returns (stream GreetReply);
    rpc GreetClientStream (stream GreetRequest) returns (GreetReply);
    rpc GreetBidirectional (stream GreetRequest) returns (stream GreetReply);
}
```

### Maven configuration

Add the `grpc-kotlin-gen` plugin to your `protobuf-maven-plugin` configuration (see [using custom protoc plugins](https://www.xolstice.org/protobuf-maven-plugin/examples/protoc-plugin.html))

```xml
<protocPlugins>
    <protocPlugin>
        <id>GrpcKotlinGenerator</id>
        <groupId>io.rouz</groupId>
        <artifactId>grpc-kotlin-gen</artifactId>
        <version>0.0.3</version>
        <mainClass>io.rouz.grpc.kotlin.GrpcKotlinGenerator</mainClass>
    </protocPlugin>
</protocPlugins>
```

### Gradle configuration

Add the `grpc-kotlin-gen` plugin to the plugins section of `protobuf-gradle-plugin`

```gradle
def protobufVersion = '3.5.1-1'
def grpcVersion = '1.15.1'

protobuf {
    protoc {
        // The artifact spec for the Protobuf Compiler
        artifact = "com.google.protobuf:protoc:${protobufVersion}"
    }
    plugins {
        grpc {
            artifact = "io.grpc:protoc-gen-grpc-java:${grpcVersion}"
        }
        grpckotlin {
            artifact = "io.rouz:grpc-kotlin-gen:0.0.3:jdk8@jar"
        }
    }
    generateProtoTasks {
        all()*.plugins {
            grpc {}
            grpckotlin {}
        }
    }
}
```

### Server

After compilation, you'll find the generated Kotlin stubs in an `object` named `GreeterGrpcKt`. Both the
service base class and client stub will use `Deferred<T>` and `{Send,Receive}Channel<T>` instead of the
typical `StreamObserver<T>` interfaces.

Here's a server implementation using some of the [core coroutine primitives] like `async` and `produce`
to create `Deferred` and `ReceiveChannel` values. Other top level primitives like `delay` are available
for use too.

```kotlin
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.produce

class GreeterImpl : GreeterGrpcKt.GreeterImplBase() {

  private val pool = newFixedThreadPoolContext(4, "server-pool")

  override fun greet(request: GreetRequest)
      : Deferred<GreetReply> = GlobalScope.async(pool) {
    GreetReply.newBuilder()
        .setReply("Hello " + request.greeting)
        .build()
  }

  override fun greetServerStream(request: GreetRequest)
      : ReceiveChannel<GreetReply> = GlobalScope.produce(pool) {
    send(GreetReply.newBuilder()
        .setReply("Hello ${request.greeting}!")
        .build())
    send(GreetReply.newBuilder()
        .setReply("Greetings ${request.greeting}!")
        .build())
  }

  override fun greetClientStream(requestChannel: ReceiveChannel<GreetRequest>)
      : Deferred<GreetReply> = GlobalScope.async(pool) {
    val greetings = mutableListOf<String>()

    for (request in requestChannel) {
      greetings.add(request.greeting)
    }

    GreetReply.newBuilder()
        .setReply("Hi to all of $greetings!")
        .build()
  }

  override fun greetBidirectional(requestChannel: ReceiveChannel<GreetRequest>)
      : ReceiveChannel<GreetReply> = GlobalScope.produce(pool) {
    var count = 0
    val queue = mutableListOf<Job>()

    for (request in requestChannel) {
      val n = count++
      val job = GlobalScope.launch(pool) {
        delay(1000)
        send(GreetReply.newBuilder()
            .setReply("Yo #$n ${request.greeting}")
            .build())
      }
      queue.add(job)
    }

    queue.forEach { it.join() }
  }
}
```

### Client

The generated client stub is also fully implemented using `Deferred` and `SendChannel`.

```kotlin
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking

fun main(args: Array<String>) {
  val localhost = ManagedChannelBuilder.forAddress("localhost", 8080)
      .usePlaintext(true)
      .build()
  val greeter = GreeterGrpcKt.newStub(localhost)

  runBlocking {
    // === Unary call =============================================================================

    val unaryResponse = greeter.greet(req("Alice")).await()
    println("unary reply = ${unaryResponse.reply}")

    // === Server streaming call ==================================================================

    val serverResponses = greeter.greetServerStream(req("Bob"))
    for (serverResponse in serverResponses) {
      println("server response = ${serverResponse.reply}")
    }

    // === Client streaming call ==================================================================

    val (reqMany, resOne) = greeter.greetClientStream()
    reqMany.send(req("Caroline"))
    reqMany.send(req("David"))
    reqMany.close()
    val oneReply = resOne.await()
    println("single reply = ${oneReply.reply}")

    // === Bidirectional call =====================================================================

    val (req, res) = greeter.greetBidirectional()
    val l = GlobalScope.launch {
      var n = 0
      for (greetReply in res) {
        println("r$n = ${greetReply.reply}")
        n++
      }
      println("no more replies")
    }

    delay(200)
    req.send(req("Eve"))

    delay(200)
    req.send(req("Fred"))

    delay(200)
    req.send(req("Gina"))

    req.close()
    l.join()
  }
}
```

## RCP method details

### Unary call

> `rpc Greet (GreetRequest) returns (GreetReply);`

#### Service

Using [`async`] coroutine builder to return a single message.

```kotlin
override fun greet(request: GreetRequest): Deferred<GreetReply> = async {
  // return GreetReply message
}
```

#### Client

Using `await()` on `Deferred<T>`.

```kotlin
val response: Deferred<GreetReply> = stub.greet( /* GreetRequest */ )
val responseMessage = response.await()
```

### Streaming request, Unary response

> `rpc GreetClientStream (stream GreetRequest) returns (GreetReply);`

#### Service

Using [`async`] coroutine builder to return a single message, and receiving messages from a `ReceiveChannel<T>`.

```kotlin
override fun greetClientStream(requestChannel: ReceiveChannel<GreetRequest>): Deferred<GreetReply> = async {
  // receive request messages
  val firstRequest = requestChannel.receive()
  
  // or iterate all request messages
  for (request in requestChannel) {
    // ...
  }

  // return GreetReply message
}
```

#### Client

Using `send()` and `close()` on `SendChannel<T>`.

```kotlin
val (requests: SendChannel<GreetRequest>, response: Deferred<GreetReply>) = stub.greetClientStream()
requests.send( /* GreetRequest */ )
requests.send( /* GreetRequest */ )
requests.close() //  don't forget to close the send channel

val responseMessage = response.await()
```

### Unary request, Streaming response

> `rpc GreetServerStream (GreetRequest) returns (stream GreetReply);`

#### Service

Using [`produce`] coroutine builder and `send` to return a stream of messages.

```kotlin
override fun greetServerStream(request: GreetRequest): ReceiveChannel<GreetReply> = GlobalScope.produce {
  send( /* GreetReply message */ )
  send( /* GreetReply message */ )
  // ...
}
```

#### Client

Using `receive()` on `ReceiveChannel<T>` or iterating with a `for` loop.

```kotlin
val responses: ReceiveChannel<GreetReply> = stub.greetServerStream( /* GreetRequest */ )

// await individual responses
val responseMessage = serverResponses.receive()

// or iterate all responses
for (responseMessage in responses) {
  // ...
}
```

### Full bidirectional streaming

> `rpc GreetBidirectional (stream GreetRequest) returns (stream GreetReply);`

#### Service

Using [`produce`] coroutine builder and `send` to return a stream of messages. Receiving messages from a `ReceiveChannel<T>`.

```kotlin
override fun greetBidirectional(requestChannel: ReceiveChannel<GreetRequest>): ReceiveChannel<GreetReply> = GlobalScope.produce {
  // receive request messages
  val firstRequest = requestChannel.receive()
  send( /* GreetReply message */ )
  
  val more = requestChannel.receive()
  send( /* GreetReply message */ )
  
  // ...
}
```

#### Client

Using both a `SendChannel<T>` and a `ReceiveChannel<T>` to interact with the call.

```kotlin
val (requests: SendChannel<GreetRequest>, responses: ReceiveChannel<GreetReply>) = stub.greetBidirectional()
val responsePrinter = GlobalScope.launch {
  for (responseMessage in responses) {
    log.info(responseMessage)
  }
  log.info("no more replies")
}

requests.send( /* GreetRequest */ )
requests.send( /* GreetRequest */ )
requests.close() //  don't forget to close the send channel

responsePrinter.join() // wait for printer coroutine to finish
```


[protoc]: https://www.xolstice.org/protobuf-maven-plugin/examples/protoc-plugin.html
[coroutine primitives]: https://github.com/Kotlin/kotlinx.coroutines
[core coroutine primitives]: https://github.com/Kotlin/kotlinx.coroutines/blob/master/core/kotlinx-coroutines-core/README.md
[`Channel`]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental.channels/-channel/index.html
[`Deferred`]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/-deferred/index.html
[`async`]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/async.html
[`produce`]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental.channels/produce.html
[gRPC]: https://grpc.io/
[reactive bindings]: https://github.com/salesforce/reactive-grpc
