# gRPC Kotlin - Coroutine based gRPC for Kotlin

[![CircleCI](https://img.shields.io/circleci/project/github/rouzwawi/grpc-kotlin.svg)](https://circleci.com/gh/rouzwawi/grpc-kotlin)
[![Maven Central](https://img.shields.io/maven-central/v/io.rouz/grpc-kotlin-gen.svg)](https://search.maven.org/#search%7Cga%7C1%7Cg%3A%22io.rouz%22%20grpc-kotlin-gen)

gRPC Kotlin is a [protoc] plugin for generating native Kotlin bindings using [coroutine primitives] for [gRPC] services.

  * [Why?](#why)
  * [Quick start](#quick-start)
     * [Server](#server)
     * [Client](#client)
  * [gRPC Context propagation](#grpc-context-propagation)
  * [Exception handling](#exception-handling)
  * [Maven configuration](#maven-configuration)
  * [Gradle configuration](#gradle-configuration)
  * [Examples](#examples)
  * [RPC method type reference](#rpc-method-type-reference)
     * [Unary call](#unary-call)
        * [Service](#service)
        * [Client](#client-1)
     * [Client streaming call](#client-streaming-call)
        * [Service](#service-1)
        * [Client](#client-2)
     * [Server streaming call](#server-streaming-call)
        * [Service](#service-2)
        * [Client](#client-3)
     * [Full bidirectional streaming call](#full-bidirectional-streaming-call)
        * [Service](#service-3)
        * [Client](#client-4)

---

## Why?

The asynchronous nature of bidirectional streaming rpc calls in gRPC makes them a bit hard to implement
and read. Getting your head around the `StreamObserver<T>`'s can be a bit tricky at times. Specially
with the method argument being the response observer and the return value being the request observer, it all
feels a bit backwards to what a plain old synchronous version of the handler would look like.

In situations where you'd want to coordinate several request and response messages in one call, you'll and up
having to manage some tricky state and synchronization between the observers. There's some [reactive bindings]
for gRPC which make this easier. But I think we can do better!

Enter Kotlin Coroutines! By generating native Kotlin stubs that allows us to use [`suspend`] functions and 
[`Channel`], we can write our handler and client code in idiomatic and easy to read Kotlin style.

## Quick start

note: This has been tested with `gRPC 1.18.0`, `protobuf 3.6.1`, `kotlin 1.3.21` and `coroutines 1.1.1`.

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

Run the protoc plugin to get the generated code, see [build tool configuration](#maven-configuration)

### Server

After compilation, you'll find the generated Kotlin code in the same package as the generated Java
code. A service base class named `GreeterImplBase` and a file with extension functions for the
client stub named `GreeterStubExt.kt`. Both the service base class and client stub extensions will
use `suspend` and `Channel<T>` instead of the typical `StreamObserver<T>` interfaces.

All functions have the [`suspend`] modifier so they can call into any suspending code, including the
[core coroutine primitives] like `delay` and `async`.

All the server streaming calls return a `ReceiveChannel<TReply>` and can easily be implemented using
`produce<TReply>`.

All client streaming calls receive an argument of `ReceiveChannel<TRequest>` where they can `receive()`
messages from the caller.

Here's an example server that demonstrates how each type of endpoint is implemented.

```kotlin
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import java.util.concurrent.Executors.newFixedThreadPool

class GreeterImpl : GreeterImplBase(
  coroutineContext = newFixedThreadPool(4).asCoroutineDispatcher()
) {

  // unary rpc
  override suspend fun greet(request: GreetRequest): GreetReply {
    return GreetReply.newBuilder()
        .setReply("Hello " + request.greeting)
        .build()
  }

  // server streaming rpc
  override fun greetServerStream(request: GreetRequest) = produce<GreetReply> {
    send(GreetReply.newBuilder()
        .setReply("Hello ${request.greeting}!")
        .build())
    send(GreetReply.newBuilder()
        .setReply("Greetings ${request.greeting}!")
        .build())
  }

  // client streaming rpc
  override suspend fun greetClientStream(requestChannel: ReceiveChannel<GreetRequest>): GreetReply {
    val greetings = mutableListOf<String>()

    for (request in requestChannel) {
      greetings.add(request.greeting)
    }

    return GreetReply.newBuilder()
        .setReply("Hi to all of $greetings!")
        .build()
  }

  // bidirectional rpc
  override fun greetBidirectional(requestChannel: ReceiveChannel<GreetRequest>) = produce<GreetReply> {
    var count = 0

    for (request in requestChannel) {
      val n = count++
      launch {
        delay(1000)
        send(GreetReply.newBuilder()
            .setReply("Yo #$n ${request.greeting}")
            .build())
      }
    }
  }
}
```

### Client

Extensions functions for the original Java stubs are generated that use `suspend` functions, `Deferred<TReply>`
and `SendChannel<TRequest>`.

```kotlin
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) {
  val localhost = ManagedChannelBuilder.forAddress("localhost", 8080)
      .usePlaintext(true)
      .build()
  val greeter = GreeterGrpc.newStub(localhost)

  runBlocking {
    // === Unary call =============================================================================

    val unaryResponse = greeter.greet(req("Alice"))
    println("unary reply = ${unaryResponse.reply}")

    // === Server streaming call ==================================================================

    val serverResponses = greeter.greetServerStream(req("Bob"))
    for (serverResponse in serverResponses) {
      println("server response = ${serverResponse.reply}")
    }

    // === Client streaming call ==================================================================

    val manyToOneCall = greeter.greetClientStream()
    manyToOneCall.send(req("Caroline"))
    manyToOneCall.send(req("David"))
    manyToOneCall.close()
    val oneReply = manyToOneCall.await()
    println("single reply = ${oneReply.reply}")

    // === Bidirectional call =====================================================================

    val bidiCall = greeter.greetBidirectional()
    launch {
      var n = 0
      for (greetReply in bidiCall) {
        println("r$n = ${greetReply.reply}")
        n++
      }
      println("no more replies")
    }

    delay(200)
    bidiCall.send(req("Eve"))

    delay(200)
    bidiCall.send(req("Fred"))

    delay(200)
    bidiCall.send(req("Gina"))

    bidiCall.close()
  }
}
```

## gRPC Context propagation

gRPC has a thread-local [`Context`] which is used to carry scoped values across API boundaries. With Kotlin coroutines
possibly being dispatched on multiple threads, the thread-local nature of `Context` needs some special care. This is
solved by two details in the generated Kotlin code.

First, all the generated service `*ImplBase` classes implement `CoroutineScope`. This allows you to use any of
the top level coroutine primitives such as `launch`, `async` and `produce` in your service implementation while still
keeping them within the context of your service code. The actual `CoroutineContext` that is used can be set through the
base class constructor, but defaults to `Dispatchers.default`.

```kotlin
abstract class MyServiceImplBase(
    coroutineContext: CoroutineContext = Dispatchers.Default
)
```

Second, in the getter for `CoroutineScope.coroutineContext`, an additional context key is added to the
`CoroutineContext` that manages the gRPC Context `attach()` and `detach()` calls when dispatching coroutine
continuations. This will ensure that the the gRPC context is always propagated across different coroutine boundaries,
and eliminates the need to manually carry it across in user code.

Here's a simple example that makes calls to other services concurrently and expects an authenticated user to be present
in the gRPC Context. The two accesses to the context key may execute on different threads in the `CoroutineContext` but
the accesses work as expected.

```kotlin
val authenticatedUser = Context.key<User>("authenticatedUser")

override suspend fun greet(request: GreetRequest): GreetReply {
    val motd = async { messageOfTheDay.getMessage() }
    val weatherReport = async { weather.getWeatherReport(authenticatedUser.get().location) }

    val reply = buildString {
        append("Hello ${authenticatedUser.get().fullName}")
        append("---")
        append("Today's weather report: ${weatherReport.await()}")
        append("---")
        append(motd.await())
    }

    return GreetReply.newBuilder()
        .setReply(reply)
        .build()
}
```

For another example of gRPC Context usage, see the code in [ContextBasedGreeterTest](grpc-kotlin-test/src/test/kotlin/io/rouz/greeter/ContextBasedGreeterTest.kt)

Thanks to [`wfhartford`](https://github.com/wfhartford) for contributing!

## Exception handling

The generated server code follows the standard exception propagation for Kotlin coroutines as described
in the [Exception handling] documentation. This means that it's safe to throw exceptions from within
the server implementation code. These will propagate up the coroutine scope and be translated to
`responseObserver.onError(Throwable)` calls. The preferred way to respond with a status code is to
throw a `StatusException`.

Note that you should not call `close(Throwable)` or `close()` from within the `ProducerScope<T>`
blocks you get from `produce` as the producer will automatically be closed when all sub-contexts are
closed (or if an exception is thrown).

## Maven configuration

Add the `grpc-kotlin-gen` plugin to your `protobuf-maven-plugin` configuration (see [compile-custom goal](https://www.xolstice.org/protobuf-maven-plugin/compile-custom-mojo.html))

```xml
<plugin>
  <groupId>org.xolstice.maven.plugins</groupId>
  <artifactId>protobuf-maven-plugin</artifactId>
  <version>0.6.1</version>
  <configuration>
    <protocArtifact>com.google.protobuf:protoc:3.5.1-1:exe:${os.detected.classifier}</protocArtifact>
  </configuration>
  <executions>
    <execution>
      <goals><goal>compile</goal></goals>
    </execution>
    <execution>
      <id>grpc-java</id>
      <goals><goal>compile-custom</goal></goals>
      <configuration>
        <pluginId>grpc-java</pluginId>
        <pluginArtifact>io.grpc:protoc-gen-grpc-java:${grpc.version}:exe:${os.detected.classifier}</pluginArtifact>
      </configuration>
    </execution>
    <execution>
      <id>grpc-kotlin</id>
      <goals><goal>compile-custom</goal></goals>
      <configuration>
        <pluginId>grpc-kotlin</pluginId>
        <pluginArtifact>io.rouz:grpc-kotlin-gen:0.1.0:jar:jdk8</pluginArtifact>
      </configuration>
    </execution>
  </executions>
</plugin>
```

_Note that this only works on unix like system at the moment._

Add the kotlin dependencies

```xml
<dependency>
  <groupId>org.jetbrains.kotlin</groupId>
  <artifactId>kotlin-stdlib</artifactId>
  <version>1.3.0</version>
</dependency>
<dependency>
  <groupId>org.jetbrains.kotlinx</groupId>
  <artifactId>kotlinx-coroutines-core</artifactId>
  <version>1.0.0</version>
</dependency>
```

Finally, make sure to add the generated source directories to the `kotlin-maven-plugin`

```xml
<plugin>
  <artifactId>kotlin-maven-plugin</artifactId>
  <groupId>org.jetbrains.kotlin</groupId>
  <version>${kotlin.version}</version>
  <executions>
    <execution>
      <id>compile</id>
      <goals><goal>compile</goal></goals>
      <configuration>
        <sourceDirs>
          <sourceDir>${project.basedir}/src/main/kotlin</sourceDir>
          <sourceDir>${project.basedir}/target/generated-sources/protobuf/grpc-kotlin</sourceDir>
          <sourceDir>${project.basedir}/target/generated-sources/protobuf/grpc-java</sourceDir>
          <sourceDir>${project.basedir}/target/generated-sources/protobuf/java</sourceDir>
        </sourceDirs>
      </configuration>
    </execution>
  </executions>
</plugin>
```

## Gradle configuration

Add the `grpc-kotlin-gen` plugin to the plugins section of `protobuf-gradle-plugin`

```gradle
def protobufVersion = '3.6.1'
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
            artifact = "io.rouz:grpc-kotlin-gen:0.1.0:jdk8@jar"
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

_Note that this only works on unix like system at the moment._

Add the kotlin dependencies

```gradle
def kotlinVersion = '1.3.0'
def kotlinCoroutinesVersion = '1.0.0'

dependencies {
    compile "org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion"
    compile "org.jetbrains.kotlinx:kotlinx-coroutines-core:kotlinCoroutinesVersion"
}
```

## Examples

This is a list of example gRPC services and clients written using this project

- [`grpc-kotlin-example-chatserver`](grpc-kotlin-example-chatserver)
- [`kotlin-grpc-sample`](https://github.com/FlavioF/kotlin-grpc-sample)
- [`grpc-death-star`](https://github.com/leveretka/grpc-death-star)

## RPC method type reference

### Unary call

> `rpc Greet (GreetRequest) returns (GreetReply);`

#### Service

A suspendable function which returns a single message.

```kotlin
override suspend fun greet(request: GreetRequest): GreetReply {
  // return GreetReply message
}
```

#### Client

Suspendable call returning a single message.

```kotlin
val response: GreetReply = stub.greet( /* GreetRequest */ )
```

### Client streaming call

> `rpc GreetClientStream (stream GreetRequest) returns (GreetReply);`

#### Service

A suspendable function which returns a single message, and receives messages from a `ReceiveChannel<T>`.

```kotlin
override suspend fun greetClientStream(requestChannel: ReceiveChannel<GreetRequest>): GreetReply {
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
val call: ManyToOneCall<GreetRequest, GreetReply> = stub.greetClientStream()
call.send( /* GreetRequest */ )
call.send( /* GreetRequest */ )
call.close() //  don't forget to close the send channel

val responseMessage = call.await()
```

### Server streaming call

> `rpc GreetServerStream (GreetRequest) returns (stream GreetReply);`

#### Service

Using `produce` and `send()` to send a stream of messages.

```kotlin
override fun greetServerStream(request: GreetRequest) = produce<GreetReply> {
  send( /* GreetReply message */ )
  send( /* GreetReply message */ )
  // ...
}
```

Note that `close()` or `close(Throwable)` should not be used, see [Exception handling](#exception-handling).

In `kotlinx-coroutines-core:1.0.0` `produce` is marked with `@ExperimentalCoroutinesApi`. In order
to use it, mark your server class with `@UseExperimental(ExperimentalCoroutinesApi::class)` and
add the `-Xuse-experimental=kotlin.Experimental` compiler flag.

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

### Full bidirectional streaming call

> `rpc GreetBidirectional (stream GreetRequest) returns (stream GreetReply);`

#### Service

Using `produce` and `send()` to send a stream of messages. Receiving messages from a `ReceiveChannel<T>`.

```kotlin
override fun greetBidirectional(requestChannel: ReceiveChannel<GreetRequest>) = produce<GreetReply> {
  // receive request messages
  val firstRequest = requestChannel.receive()
  send( /* GreetReply message */ )
  
  val more = requestChannel.receive()
  send( /* GreetReply message */ )
  
  // ...
}
```

Note that `close()` or `close(Throwable)` should not be used, see [Exception handling](#exception-handling).

In `kotlinx-coroutines-core:1.0.0` `produce` is marked with `@ExperimentalCoroutinesApi`. In order
to use it, mark your server class with `@UseExperimental(ExperimentalCoroutinesApi::class)` and
add the `-Xuse-experimental=kotlin.Experimental` compiler flag.

#### Client

Using both a `SendChannel<T>` and a `ReceiveChannel<T>` to interact with the call.

```kotlin
val call: ManyToManyCall<GreetRequest, GreetReply> = stub.greetBidirectional()
launch {
  for (responseMessage in call) {
    log.info(responseMessage)
  }
  log.info("no more replies")
}

call.send( /* GreetRequest */ )
call.send( /* GreetRequest */ )
call.close() //  don't forget to close the send channel
```


[protoc]: https://www.xolstice.org/protobuf-maven-plugin/examples/protoc-plugin.html
[`suspend`]: https://kotlinlang.org/docs/reference/coroutines-overview.html
[coroutine primitives]: https://github.com/Kotlin/kotlinx.coroutines
[core coroutine primitives]: https://github.com/Kotlin/kotlinx.coroutines/blob/master/core/kotlinx-coroutines-core/README.md
[Exception handling]: https://kotlinlang.org/docs/reference/coroutines/exception-handling.html
[`Channel`]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental.channels/-channel/index.html
[`Deferred`]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/-deferred/index.html
[`async`]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/async.html
[`produce`]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental.channels/produce.html
[`Context`]: https://grpc.io/grpc-java/javadoc/io/grpc/Context.html
[gRPC]: https://grpc.io/
[reactive bindings]: https://github.com/salesforce/reactive-grpc

