package com.soap4tv.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.soap4tv.app.data.network.SoapCookieJar
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class Soap4TvApp : Application(), SingletonImageLoader.Factory {

    @Inject
    lateinit var imageLoader: ImageLoader

    @Inject
    lateinit var cookieJar: SoapCookieJar

    override fun onCreate() {
        super.onCreate()
        // Decrypting the stored jar is blocking work; do it before the first request needs it.
        cookieJar.prewarm()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader
}
