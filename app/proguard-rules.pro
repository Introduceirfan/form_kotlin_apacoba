# Aturan R8/ProGuard. M1 tidak pakai minify, jadi file ini hanya placeholder.
# Kalau nanti minify diaktifkan, tambahkan aturan untuk @JavascriptInterface
# supaya method bridge tidak ikut di-obfuscate:
#-keepclassmembers class com.tambalban.form.WebAppBridge {
#    public *;
#}
