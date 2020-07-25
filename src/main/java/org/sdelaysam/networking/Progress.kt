package org.sdelaysam.networking

/**
 * Created on 6/15/20.
 * @author sdelaysam
 */

sealed class Progress {
    class Upload(val bytesCompleted: Long, val bytesTotal: Long, val fractionCompleted: Double): Progress()
    class Download(val bytesCompleted: Long, val bytesTotal: Long, val fractionCompleted: Double): Progress()
    object Complete: Progress()
}

sealed class ProgressWithResult<T: Any> {
    class Upload<T: Any>(val bytesCompleted: Long, val bytesTotal: Long, val fractionCompleted: Double): ProgressWithResult<T>()
    class Download<T: Any>(val bytesCompleted: Long, val bytesTotal: Long, val fractionCompleted: Double): ProgressWithResult<T>()
    class Complete<T: Any>(val data: T): ProgressWithResult<T>()
}