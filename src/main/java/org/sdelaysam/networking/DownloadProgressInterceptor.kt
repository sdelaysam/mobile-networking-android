package org.sdelaysam.networking

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Created on 6/15/20.
 * @author sdelaysam
 */

class DownloadProgressInterceptor(private val listener: ProgressListener) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        return response.newBuilder()
            .body(ProgressResponseBody(response.body!!, listener))
            .build()
    }
}