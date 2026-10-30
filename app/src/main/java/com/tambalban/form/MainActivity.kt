package com.tambalban.form

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import java.net.URI

/**
 * Satu-satunya halaman app: WebView yang memuat portal /form di server Laravel.
 *
 * Alur singkat:
 * onCreate -> setupWebView() -> loadUrl(BASE_URL + "/form")
 * Hasil scan / aksi native nanti masuk lewat evaluateJavascript(
 *   "window.setFormField('lot_no', '...')"
 * ) yang kontraknya sudah disiapkan di resources/views/pages/form/entry.blade.php.
 */
class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorPanel: View

    // Permintaan kamera dari halaman web (html5-qrcode) yang menunggu izin Android.
    private var pendingCameraRequest: PermissionRequest? = null

    private var lastBackPressAt = 0L

    // BASE_URL diisi build.gradle.kts (debug: LAN/emulator, release: domain HTTPS).
    private val homeUrl: String by lazy { BuildConfig.BASE_URL.trimEnd('/') + "/form" }

    // Host yang boleh dibuka WebView — diambil dari BASE_URL, jadi otomatis
    // ikut kalau alamat server diganti.
    private val allowedHost: String by lazy { URI(homeUrl).host ?: "" }

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val request = pendingCameraRequest
        pendingCameraRequest = null
        if (request == null) return@registerForActivityResult

        if (granted) {
            request.grant(request.resources)
        } else {
            request.deny()
            toast(getString(R.string.camera_denied))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        errorPanel = findViewById(R.id.errorPanel)

        // Operator sering menunggu lama di satu form; jangan sampai layar mati.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setupWebView()
        findViewById<Button>(R.id.retryButton).setOnClickListener { retry() }

        // savedInstanceState != null artinya activity dibuat ulang (mis. rotasi),
        // jadi pulihkan posisi halaman terakhir, bukan load ulang dari portal.
        if (savedInstanceState == null) {
            webView.loadUrl(homeUrl)
        } else {
            webView.restoreState(savedInstanceState)
        }

        // Tombol back: kembali ke halaman sebelumnya di dalam app.
        // Di halaman paling awal, tekan 2x untuk keluar (biar tidak kepencet).
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                    return
                }

                val now = System.currentTimeMillis()
                if (now - lastBackPressAt < 2000) {
                    finish()
                } else {
                    lastBackPressAt = now
                    toast(getString(R.string.exit_hint))
                }
            }
        })
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true   // Alpine.js & form logic
            domStorageEnabled = true   // localStorage/sessionStorage dipakai app.js
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

            // Penanda di user agent supaya request dari app bisa dibedakan di server.
            userAgentString = userAgentString + " FormApp/" + BuildConfig.VERSION_NAME
        }

        // Jembatan JS -> native, dipanggil sebagai window.AndroidBridge.*
        webView.addJavascriptInterface(WebAppBridge(this), "AndroidBridge")

        // Debug isi WebView dari Chrome desktop: chrome://inspect
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url
                val isHttp = url.scheme == "http" || url.scheme == "https"

                // Hanya host form yang boleh dibuka. Link ke luar (Google,
                // WA, dsb) diblokir supaya app tidak jadi browser.
                if (isHttp && url.host == allowedHost) return false

                toast(getString(R.string.link_blocked))
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                errorPanel.visibility = View.GONE
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                // Error aset kecil (gambar/font) diabaikan; yang ditangani
                // hanya kegagalan halaman utama (server mati / WiFi putus).
                if (!request.isForMainFrame) return

                Log.w("FormApp", "Gagal memuat ${request.url}: ${error.description}")
                showError()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {

            override fun onPermissionRequest(request: PermissionRequest) {
                // html5-qrcode minta kamera lewat getUserMedia -> diteruskan
                // ke izin Android, lalu di-grant balik ke halaman web.
                if (request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                    askCameraIfNeeded(request)
                } else {
                    request.deny()
                }
            }
        }
    }

    private fun askCameraIfNeeded(request: PermissionRequest) {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

        if (granted) {
            request.grant(request.resources)
            return
        }

        pendingCameraRequest = request
        cameraPermission.launch(Manifest.permission.CAMERA)
    }

    /** Dipakai tombol "Coba Lagi" dan JS lewat AndroidBridge.retry(). */
    fun retry() {
        errorPanel.visibility = View.GONE
        webView.loadUrl(homeUrl)
    }

    fun toast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showError() {
        progressBar.visibility = View.GONE
        errorPanel.visibility = View.VISIBLE
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }
}
