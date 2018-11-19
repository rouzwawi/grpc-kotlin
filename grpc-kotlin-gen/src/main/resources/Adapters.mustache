package io.rouz.grpc

import io.grpc.Context
import io.grpc.stub.StreamObserver
import kotlin.coroutines.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

class ManyToOneCall<in TRequest, out TResponse>(
    private val request: StreamObserver<TRequest>,
    private val response: Deferred<TResponse>
) : StreamObserverSendAdapter<TRequest>(request),
    Deferred<TResponse> by response

class ManyToManyCall<in TRequest, out TResponse>(
    private val request: StreamObserver<TRequest>,
    private val response: ReceiveChannel<TResponse>
) : StreamObserverSendAdapter<TRequest>(request),
    ReceiveChannel<TResponse> by response

open class StreamObserverSendAdapter<in E>(private val streamObserver: StreamObserver<E>) {

    fun close(cause: Throwable? = null): Boolean {
        if (cause != null) {
            streamObserver.onError(cause)
        } else {
            streamObserver.onCompleted()
        }

        return true
    }

    fun send(element: E) {
        streamObserver.onNext(element)
    }
}

class ContinuationStreamObserver<E>(
    private val continuation: Continuation<E>
) : StreamObserver<E> {

    override fun onNext(value: E) {
        continuation.resume(value)
    }

    override fun onError(t: Throwable) {
        continuation.resumeWithException(t)
    }

    override fun onCompleted() {}
}

class StreamObserverDeferred<E>(
    private val deferred: CompletableDeferred<E> = CompletableDeferred()
) : StreamObserver<E>, Deferred<E> by deferred {

    override fun onNext(value: E) {
        deferred.complete(value)
    }

    override fun onError(t: Throwable) {
        deferred.completeExceptionally(t)
    }

    override fun onCompleted() { /* nothing */
    }
}

class StreamObserverChannel<E>(
    private val channel: Channel<E> = Channel<E>(Channel.UNLIMITED)
) : StreamObserver<E>, ReceiveChannel<E> by channel {

    override fun onNext(value: E) {
        channel.offer(value)
    }

    override fun onError(t: Throwable?) {
        channel.close(cause = t)
    }

    override fun onCompleted() {
        channel.close(cause = null)
    }
}

class ContextCoroutineContextElement : ThreadContextElement<Context> {

    companion object Key : CoroutineContext.Key<ContextCoroutineContextElement>

    private val grpcContext: Context = Context.current()

    override val key: CoroutineContext.Key<ContextCoroutineContextElement>
        get() = Key

    override fun updateThreadContext(context: CoroutineContext): Context =
        grpcContext.attach()

    override fun restoreThreadContext(context: CoroutineContext, oldState: Context) =
        grpcContext.detach(oldState)
}
