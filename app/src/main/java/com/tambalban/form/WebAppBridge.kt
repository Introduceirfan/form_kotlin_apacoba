package com.tambalban.form

import android.annotation.SuppressLint
import android.provider.Settings
import android.webkit.JavascriptInterface

/**
 * Jembatan JS -> native. Dari halaman web dipanggil sebagai window.AndroidBridge.
 *
 * Sengaja hanya berisi fungsi baca-saja + toast + retry. Jangan tambah fungsi
 * yang bisa menulis file atau menjalankan perintah: apa pun yang diekspos di
 * sini bisa dipanggil oleh JS halaman.
 */
class WebAppBridge(private val activity: MainActivity) {

    @JavascriptInterface
    fun platform(): String = "android"

    @JavascriptInterface
    fun appVersion(): String = BuildConfig.VERSION_NAME

    /**
     * ID perangkat (bukan identitas user). Nanti bisa dipakai untuk whitelist
     * device di server tanpa menambah permission berbahaya.
     */
    @SuppressLint("HardwareIds")
    @JavascriptInterface
    fun deviceId(): String =
        Settings.Secure.getString(activity.contentResolver, Settings.Secure.ANDROID_ID) ?: ""

    @JavascriptInterface
    fun toast(message: String) {
        activity.toast(message)
    }

    @JavascriptInterface
    fun retry() {
        activity.retry()
    }
}
