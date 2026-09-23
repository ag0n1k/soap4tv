package com.soap4tv.app.di

import android.app.Application
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.soap4tv.app.data.network.ImageHttp
import com.soap4tv.app.data.network.PlayerHttp
import com.soap4tv.app.data.network.PlayerHttpEventListener
import com.soap4tv.app.data.network.RetryInterceptor
import com.soap4tv.app.data.network.SoapCookieJar
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(cookieJar: SoapCookieJar): OkHttpClient {
        return OkHttpClient.Builder()
            .cookieJar(cookieJar)
            // Everything lives on one host, and OkHttp's default cap of 5 requests per host
            // is shared queue-wide: the startup burst of catalog pages would otherwise block
            // each other. Posters have their own client below.
            .dispatcher(Dispatcher().apply { maxRequestsPerHost = 8 })
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .addInterceptor(RetryInterceptor())
            .build()
    }

    // Dedicated client for HLS segment traffic. Separated from the shared client so that
    // image loading (Coil) and API calls don't compete with video for connections, and so
    // we can give the player a longer readTimeout suitable for 4K segments.
    @Provides
    @Singleton
    @PlayerHttp
    fun providePlayerOkHttpClient(cookieJar: SoapCookieJar): OkHttpClient =
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .addInterceptor(RetryInterceptor())
            .eventListenerFactory { PlayerHttpEventListener() }
            .build()

    /**
     * Posters get their own client so they queue independently of API traffic. Sharing one
     * meant a screenful of covers waited behind the catalog HTML (same host, same dispatcher)
     * and vice versa. No retry interceptor here: a poster that 503s is not worth parking a
     * thread in `Thread.sleep` — it will be requested again when it scrolls back into view.
     */
    @Provides
    @Singleton
    @ImageHttp
    fun provideImageOkHttpClient(cookieJar: SoapCookieJar): OkHttpClient =
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .dispatcher(Dispatcher().apply { maxRequestsPerHost = 8 })
            .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

    @Provides
    @Singleton
    fun provideImageLoader(
        application: Application,
        @ImageHttp okHttpClient: OkHttpClient
    ): ImageLoader {
        return ImageLoader.Builder(application)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = okHttpClient))
            }
            .build()
    }
}
