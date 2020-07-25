package org.sdelaysam.networking

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Created on 6/16/20.
 * @author sdelaysam
 */

class UploadProgressInterceptor(private val listener: ProgressListener) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        if (request.body != null) {
            request = request.newBuilder()
                .method(request.method, ProgressRequestBody(request.body!!, listener))
                .build()
        }
        return chain.proceed(request)
    }
}