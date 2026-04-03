package com.soap4tv.app.data.network

import java.security.MessageDigest

object Md5Util {
    fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Computes the play hash for series episodes.
     * Formula: md5(token + eid + sid + hash)
     * where hash = data:hash attribute from play button element
     */
    fun computePlayHash(token: String, eid: String, sid: String, hash: String): String {
        return md5(token + eid + sid + hash)
    }
}
