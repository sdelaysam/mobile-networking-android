package org.sdelaysam.networking

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Created on 6/14/20.
 * @author sdelaysam
 */

abstract class BaseService constructor(
    private val processor: NetworkRequestProcessor,
    private val hostProvider: HostProvider,
    private val json: Json
) {

    companion object {
        private val JSON_MIME = "application/json".toMediaTypeOrNull()
    }

    fun get(path: String): NetworkRequestBuilder {
        return NetworkRequestBuilder(hostProvider.host, hostProvider.scheme)
            .withMethod(HttpMethod.GET)
            .withPath(path)
    }

    fun post(path: String): NetworkRequestBuilder {
        return NetworkRequestBuilder(hostProvider.host, hostProvider.scheme)
            .withMethod(HttpMethod.POST)
            .withPath(path)
    }

    protected fun <T: Any> NetworkRequestBuilder.withBody(body: T, serializer: KSerializer<T>): NetworkRequestBuilder {
        return withBody(json.toJson(serializer, body).toString().toRequestBody(JSON_MIME))
    }

    protected fun <T: Any> NetworkRequestBuilder.asSingle(serializer: KSerializer<T>): Single<T> {
        return processor.process(this, serializer)
    }

    protected fun NetworkRequestBuilder.asCompletable(): Completable {
        return processor.process(this)
    }

    protected fun <T: Any> NetworkRequestBuilder.asProgressWithResult(serializer: KSerializer<T>): Observable<ProgressWithResult<T>> {
        return processor.processWithProgress(this, serializer)
    }

    protected fun NetworkRequestBuilder.asProgress(): Observable<Progress> {
        return processor.processWithProgress(this)
    }

}