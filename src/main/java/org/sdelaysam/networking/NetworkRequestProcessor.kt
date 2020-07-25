package org.sdelaysam.networking

import io.reactivex.*
import io.reactivex.exceptions.CompositeException
import io.reactivex.exceptions.Exceptions
import io.reactivex.plugins.RxJavaPlugins
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import okhttp3.*
import java.io.IOException

/**
 * Created on 6/14/20.
 * @author sdelaysam
 */

interface NetworkRequestProcessor {
    fun <T : Any> process(request: NetworkRequest, serializer: KSerializer<T>): Single<T>

    fun process(request: NetworkRequest): Completable

    fun <T : Any> processWithProgress(
        request: NetworkRequest,
        serializer: KSerializer<T>
    ): Observable<ProgressWithResult<T>>

    fun processWithProgress(request: NetworkRequest): Observable<Progress>
}

class DefaultNetworkRequestProcessor(
    private val client: OkHttpClient,
    private val tokenRefresher: TokenRefresher,
    private val json: Json,
    private val errorBodyConverter: ErrorBodyConverter
) : NetworkRequestProcessor {

    override fun <T : Any> process(request: NetworkRequest, serializer: KSerializer<T>): Single<T> {
        return tokenRefresher.await()
            .andThen(singleFromRequest(request, serializer))
            .retryWhen { it.tryRefreshToken() }
    }

    override fun process(request: NetworkRequest): Completable {
        return tokenRefresher.await()
            .andThen(completableFromRequest(request))
            .retryWhen { it.tryRefreshToken() }
    }

    override fun <T : Any> processWithProgress(
        request: NetworkRequest,
        serializer: KSerializer<T>
    ): Observable<ProgressWithResult<T>> {
        return tokenRefresher.await()
            .andThen(progressWithResultObservableFromRequest(request, serializer))
            .retryWhen { it.tryRefreshToken() }
    }

    override fun processWithProgress(request: NetworkRequest): Observable<Progress> {
        return tokenRefresher.await()
            .andThen(progressObservableFromRequest(request))
            .retryWhen { it.tryRefreshToken() }
    }

    private fun <T : Any> singleFromRequest(
        request: NetworkRequest,
        serializer: KSerializer<T>
    ): Single<T> {
        return Single.create { observer ->
            val call = request.callWithClient(client)

            if (observer.isDisposed) return@create

            call.process(onSuccess = {
                val result = json.parse(serializer, it.body!!.string())
                observer.onSuccess(result)
            }, onError = {
                throwError(it, observer)
            }, isDisposed = {
                observer.isDisposed
            })

            observer.setCancellable {
                call.cancel()
            }
        }
    }

    private fun completableFromRequest(request: NetworkRequest): Completable {
        return Completable.create { observer ->
            val call = request.callWithClient(client)

            if (observer.isDisposed) return@create

            call.process(onSuccess = {
                observer.onComplete()
            }, onError = {
                throwError(it, observer)
            }, isDisposed = {
                observer.isDisposed
            })

            observer.setCancellable {
                call.cancel()
            }
        }
    }

    private fun <T : Any> progressWithResultObservableFromRequest(
        request: NetworkRequest,
        serializer: KSerializer<T>
    ): Observable<ProgressWithResult<T>> {
        return Observable.create { observer ->

            val newClient = client.newBuilder()
                .addNetworkInterceptor(UploadProgressInterceptor(object: ProgressListener {
                    override fun onProgress(bytesCompleted: Long, bytesTotal: Long, fractionCompleted: Double) {
                        if (!observer.isDisposed) {
                            observer.onNext(ProgressWithResult.Upload(bytesCompleted, bytesTotal, fractionCompleted))
                        }
                    }
                }))
                .addNetworkInterceptor(DownloadProgressInterceptor(object: ProgressListener {
                    override fun onProgress(bytesCompleted: Long, bytesTotal: Long, fractionCompleted: Double) {
                        if (!observer.isDisposed) {
                            observer.onNext(ProgressWithResult.Download(bytesCompleted, bytesTotal, fractionCompleted))
                        }
                    }
                }))
                .build()

            val call = request.callWithClient(newClient)

            if (observer.isDisposed) return@create

            call.process(onSuccess = {
                val result = json.parse(serializer, it.body!!.string())
                observer.onNext(ProgressWithResult.Complete(result))
                observer.onComplete()
            }, onError = {
                throwError(it, observer)
            }, isDisposed = {
                observer.isDisposed
            })

            observer.setCancellable {
                call.cancel()
            }
        }
    }

    private fun progressObservableFromRequest(request: NetworkRequest): Observable<Progress> {
        return Observable.create { observer ->

            val newClient = client.newBuilder()
                .addNetworkInterceptor(UploadProgressInterceptor(object: ProgressListener {
                    override fun onProgress(bytesCompleted: Long, bytesTotal: Long, fractionCompleted: Double) {
                        if (!observer.isDisposed) {
                            observer.onNext(Progress.Upload(bytesCompleted, bytesTotal, fractionCompleted))
                        }
                    }
                }))
                .addNetworkInterceptor(DownloadProgressInterceptor(object: ProgressListener {
                    override fun onProgress(bytesCompleted: Long, bytesTotal: Long, fractionCompleted: Double) {
                        if (!observer.isDisposed) {
                            observer.onNext(Progress.Download(bytesCompleted, bytesTotal, fractionCompleted))
                        }
                    }
                }))
                .build()

            val call = request.callWithClient(newClient)

            if (observer.isDisposed) return@create

            call.process(onSuccess = {
                observer.onNext(Progress.Complete)
                observer.onComplete()
            }, onError = {
                throwError(it, observer)
            }, isDisposed = {
                observer.isDisposed
            })

            observer.setCancellable {
                call.cancel()
            }
        }
    }

    private fun <T> throwError(error: Throwable, emitter: SingleEmitter<T>) {
        try {
            emitter.onError(error)
        } catch (e: Exception) {
            Exceptions.throwIfFatal(e)
            RxJavaPlugins.onError(CompositeException(error, e))
        }
    }

    private fun throwError(error: Throwable, emitter: CompletableEmitter) {
        try {
            emitter.onError(error)
        } catch (e: Exception) {
            Exceptions.throwIfFatal(e)
            RxJavaPlugins.onError(CompositeException(error, e))
        }
    }

    private fun <T> throwError(error: Throwable, emitter: ObservableEmitter<T>) {
        try {
            emitter.onError(error)
        } catch (e: Exception) {
            Exceptions.throwIfFatal(e)
            RxJavaPlugins.onError(CompositeException(error, e))
        }
    }

    private fun Observable<Throwable>.tryRefreshToken(): Observable<Unit> {
        return flatMap { tokenRefresher.tryRefresh(it).toObservable() }
    }

    private fun Flowable<Throwable>.tryRefreshToken(): Flowable<Unit> {
        return flatMap { tokenRefresher.tryRefresh(it).toFlowable() }
    }

    private fun NetworkRequest.callWithClient(client: OkHttpClient): Call {
        return client.newCall(Request.Builder()
            .url(url)
            .method(method.name, body)
            .apply { headers?.let { headers(it) } }
            .build())
    }

    private fun Call.process(onSuccess: (Response) -> Unit, onError: (Throwable) -> Unit, isDisposed: () -> Boolean) {
        enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                if (call.isCanceled()) return
                onError(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (isDisposed()) return

                if (response.isSuccessful) {
                    try {
                        onSuccess(response)
                    } catch (e: Exception) {
                        Exceptions.throwIfFatal(e)
                        if (!isDisposed()) {
                            onError(e)
                        }
                    }
                } else {
                    onError(errorBodyConverter.convert(response) ?: Throwable(response.message))
                }
            }
        })

    }

}