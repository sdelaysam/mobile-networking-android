package org.sdelaysam.networking

/**
 * Created on 7/25/20.
 * @author sdelaysam
 */

interface HostProvider {
    val scheme: String
    val host: String
}