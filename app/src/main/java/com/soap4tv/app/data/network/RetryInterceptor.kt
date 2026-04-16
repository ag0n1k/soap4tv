package com.soap4tv.app.data.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Retries on transient server errors (429, 502, 503, 504) with exponential backoff.
 * Does not retry on 4xx other than 429 — those mean the request itself is wrong.
 */
class RetryInterceptor(
    private val maxAttempts: Int = 3,
    private val initialBackoffMs: Long = 500L
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var attempt = 0
        var response: Response = chain.proceed(chain.request())
        while (attempt < maxAttempts - 1 && response.shouldRetry()) {
            response.close()
            val delay = initialBackoffMs shl attempt
            try {
                Thread.sleep(delay)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
            attempt++
            response = chain.proceed(chain.request())
        }
        return response
    }

    private fun Response.shouldRetry(): Boolean = code in RETRYABLE_CODES

    companion object {
        private val RETRYABLE_CODES = setOf(429, 502, 503, 504)
    }
}
