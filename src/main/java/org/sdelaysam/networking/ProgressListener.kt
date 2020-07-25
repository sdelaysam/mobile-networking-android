package org.sdelaysam.networking

import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okio.*
import java.io.IOException

/**
 * Created on 6/15/20.
 * @author sdelaysam
 */

interface ProgressListener {
    fun onProgress(bytesCompleted: Long, bytesTotal: Long, fractionCompleted: Double)
}

class ProgressRequestBody(
    private val requestBody: RequestBody,
    private val listener: ProgressListener
): RequestBody() {

    private var completed = false // https://github.com/square/okhttp/issues/3077

    override fun contentType(): MediaType? {
        return requestBody.contentType()
    }

    override fun contentLength(): Long {
        return requestBody.contentLength()
    }

    override fun writeTo(sink: BufferedSink) {
        val totalBytes = contentLength()
        val progressSink = object : ForwardingSink(sink) {
            private var completedBytes = 0L

            @Throws(IOException::class)
            override fun write(source: Buffer, byteCount: Long) {
                completedBytes += byteCount
                if (!completed) {
                    listener.onProgress(completedBytes, totalBytes, completedBytes.toDouble()/totalBytes)
                }
                completed = completedBytes == totalBytes
                super.write(source, byteCount)
            }
        }.buffer()
        requestBody.writeTo(progressSink)
        progressSink.flush()
    }

}

class ProgressResponseBody(
    private val responseBody: ResponseBody,
    private val listener: ProgressListener
) : ResponseBody() {

    private var bufferedSource: BufferedSource? = null

    private var completed: Boolean = false // https://github.com/square/okhttp/issues/3077

    override fun contentLength(): Long {
        return responseBody.contentLength()
    }

    override fun contentType(): MediaType? {
        return responseBody.contentType()
    }

    override fun source(): BufferedSource {
        if (bufferedSource == null) {
            bufferedSource = source(responseBody.source()).buffer()
        }
        return bufferedSource!!
    }

    private fun source(source: Source): Source {
        val totalBytes = contentLength()
        return object : ForwardingSource(source) {
            var completedBytes = 0L

            @Throws(IOException::class)
            override fun read(sink: Buffer, byteCount: Long): Long {
                val bytesRead = super.read(sink, byteCount)
                // read() returns the number of bytes read, or -1 if this source is exhausted.
                completedBytes += if (bytesRead != -1L) bytesRead else 0
                if (!completed) {
                    listener.onProgress(completedBytes, totalBytes, completedBytes.toDouble()/totalBytes)
                }
                completed = completedBytes == totalBytes
                return bytesRead
            }
        }
    }
}