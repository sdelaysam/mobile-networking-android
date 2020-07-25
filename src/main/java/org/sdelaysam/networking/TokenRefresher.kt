package org.sdelaysam.networking

import io.reactivex.Completable
import io.reactivex.Single

/**
 * Created on 6/7/20.
 * @author sdelaysam
 */

interface TokenRefresher {
    fun await(): Completable
    fun tryRefresh(throwable: Throwable): Single<Unit>
}

class NoOpTokenRefresher : TokenRefresher {

    override fun await(): Completable {
        return Completable.complete()
    }

    override fun tryRefresh(throwable: Throwable): Single<Unit> {
        return Single.error(throwable)
    }
}