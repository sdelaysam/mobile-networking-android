package org.sdelaysam.networking

import okhttp3.Response

/**
 * Created on 7/25/20.
 * @author sdelaysam
 */

interface ErrorBodyConverter {
    fun convert(response: Response): Throwable?
}