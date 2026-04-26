package com.soap4tv.app.data.network

import android.os.SystemClock
import android.util.Log
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Response
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy

class PlayerHttpEventListener : EventListener() {

    private var callStartedMs: Long = 0L
    private var connectStartedMs: Long = 0L

    override fun callStart(call: Call) {
        callStartedMs = SystemClock.elapsedRealtime()
        PlayerHttpStats.update { it.copy(totalCalls = it.totalCalls + 1) }
    }

    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
        connectStartedMs = SystemClock.elapsedRealtime()
        Log.i(TAG, "connectStart host=${inetSocketAddress.hostString}:${inetSocketAddress.port}")
    }

    override fun connectEnd(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: okhttp3.Protocol?
    ) {
        val took = SystemClock.elapsedRealtime() - connectStartedMs
        Log.i(TAG, "connectEnd in ${took}ms proto=${protocol?.toString() ?: "?"}")
        PlayerHttpStats.update {
            it.copy(
                totalConnects = it.totalConnects + 1,
                lastConnectMs = took
            )
        }
    }

    override fun connectFailed(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: okhttp3.Protocol?,
        ioe: IOException
    ) {
        val took = SystemClock.elapsedRealtime() - connectStartedMs
        Log.w(TAG, "connectFailed after ${took}ms host=${inetSocketAddress.hostString} err=${ioe.javaClass.simpleName}: ${ioe.message}")
        PlayerHttpStats.update {
            it.copy(
                totalErrors = it.totalErrors + 1,
                lastErrorClass = ioe.javaClass.simpleName,
                lastErrorMessage = ioe.message ?: ""
            )
        }
    }

    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
        if (inetAddressList.isEmpty()) {
            Log.w(TAG, "dnsEnd $domainName -> EMPTY")
        }
    }

    override fun responseHeadersEnd(call: Call, response: Response) {
        val req = call.request()
        val range = req.header("Range") ?: "-"
        val connection = response.header("Connection") ?: "-"
        val acceptRanges = response.header("Accept-Ranges") ?: "-"
        val contentLength = response.header("Content-Length") ?: "-"
        val contentType = response.header("Content-Type") ?: "-"
        val took = SystemClock.elapsedRealtime() - callStartedMs
        Log.i(
            TAG,
            "headers code=${response.code} ttfb=${took}ms range=$range " +
                "len=$contentLength type=$contentType conn=$connection accept=$acceptRanges " +
                "url=${req.url}"
        )
        val urlString = req.url.toString()
        val tail = if (urlString.length > 60) "…" + urlString.takeLast(60) else urlString
        val lenLong = contentLength.toLongOrNull() ?: 0L
        PlayerHttpStats.update {
            it.copy(
                lastTtfbMs = took,
                lastResponseCode = response.code,
                lastContentLengthBytes = lenLong,
                lastConnHeader = connection,
                lastUrlTail = tail
            )
        }
    }

    override fun callEnd(call: Call) {
        val took = SystemClock.elapsedRealtime() - callStartedMs
        if (took > 5_000) {
            Log.w(TAG, "callEnd SLOW ${took}ms url=${call.request().url}")
        }
    }

    override fun callFailed(call: Call, ioe: IOException) {
        val took = SystemClock.elapsedRealtime() - callStartedMs
        Log.w(
            TAG,
            "callFailed after ${took}ms err=${ioe.javaClass.simpleName}: ${ioe.message} url=${call.request().url}"
        )
        PlayerHttpStats.update {
            it.copy(
                totalErrors = it.totalErrors + 1,
                lastErrorClass = ioe.javaClass.simpleName,
                lastErrorMessage = ioe.message ?: ""
            )
        }
    }

    companion object {
        private const val TAG = "Soap4tvPlayerHttp"
    }
}
