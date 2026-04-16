package com.soap4tv.app.di

import android.app.Application
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.soap4tv.app.data.network.RetryInterceptor
import com.soap4tv.app.data.network.SoapCookieJar
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
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
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .addInterceptor(RetryInterceptor())
            .build()
    }

    @Provides
    @Singleton
    fun provideImageLoader(application: Application, okHttpClient: OkHttpClient): ImageLoader {
        return ImageLoader.Builder(application)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = okHttpClient))
            }
            .build()
    }
}
