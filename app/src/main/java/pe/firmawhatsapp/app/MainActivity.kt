package pe.firmawhatsapp.app

import android.app.Activity
import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.ValueCallback
import android.widget.Toast
import org.json.JSONObject
import java.io.OutputStream

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private var pageLoaded = false
    private var pendingPdfUri: Uri? = null
    private var pendingFileChooserCallback: ValueCallback<Array<Uri>>? = null
    @Volatile private var lastSavedPdfUri: Uri? = null

    companion object {
        private const val FILE_CHOOSER_REQUEST_CODE = 9202
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(18, 107, 80)
        window.navigationBarColor = Color.rgb(18, 107, 80)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        webView = WebView(this)
        webView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        webView.setBackgroundColor(Color.rgb(243, 246, 244))
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
        webView.settings.setSupportMultipleWindows(false)
        webView.addJavascriptInterface(AndroidFiles(), "AndroidFiles")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                pageLoaded = true
                deliverPendingPdf()
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url ?: return false
                if (url.scheme == "https" &&
                    url.host != "cdnjs.cloudflare.com" &&
                    url.host != "unpkg.com") {
                    startActivity(Intent(Intent.ACTION_VIEW, url))
                    return true
                }
                return false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: WebChromeClient.FileChooserParams?
            ): Boolean {
                pendingFileChooserCallback?.onReceiveValue(null)
                pendingFileChooserCallback = filePathCallback
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/pdf"
                }
                return try {
                    startActivityForResult(
                        Intent.createChooser(intent, "Selecciona un PDF"),
                        FILE_CHOOSER_REQUEST_CODE
                    )
                    true
                } catch (_: Exception) {
                    pendingFileChooserCallback?.onReceiveValue(null)
                    pendingFileChooserCallback = null
                    false
                }
            }
        }
        setContentView(webView)
        receiveIncomingIntent(intent)
        webView.loadUrl("file:///android_asset/index.html")
    }

    @Deprecated("Deprecated Android API retained for WebView file chooser compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != FILE_CHOOSER_REQUEST_CODE) return

        val callback = pendingFileChooserCallback ?: return
        pendingFileChooserCallback = null
        val selectedUris: Array<Uri>? = if (resultCode == RESULT_OK) {
            val clipData = data?.clipData
            when {
                clipData != null -> Array(clipData.itemCount) { index -> clipData.getItemAt(index).uri }
                data?.data != null -> arrayOf(data.data!!)
                else -> null
            }
        } else {
            null
        }
        callback.onReceiveValue(selectedUris)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveIncomingIntent(intent)
    }

    private fun receiveIncomingIntent(incoming: Intent?) {
        if (incoming == null) return
        val uri: Uri? = when (incoming.action) {
            Intent.ACTION_VIEW -> incoming.data
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                if (Build.VERSION.SDK_INT >= 33) {
                    incoming.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    incoming.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            }
            else -> null
        }
        if (uri != null && (incoming.type == "application/pdf" ||
                    uri.toString().lowercase().endsWith(".pdf"))) {
            pendingPdfUri = uri
            if (pageLoaded) deliverPendingPdf()
        }
    }

    private fun deliverPendingPdf() {
        val uri = pendingPdfUri ?: return
        pendingPdfUri = null
        Thread {
            try {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("No se pudo leer el PDF")
                val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val name = queryDisplayName(uri) ?: "documento.pdf"
                val js = "window.loadIncomingPdfFromAndroid(" +
                    JSONObject.quote(encoded) + "," + JSONObject.quote(name) + ");"
                runOnUiThread { webView.evaluateJavascript(js, null) }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "No se pudo abrir el PDF recibido", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun queryDisplayName(uri: Uri): String? {
        var cursor: Cursor? = null
        return try {
            cursor = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else null
        } catch (_: Exception) {
            null
        } finally {
            cursor?.close()
        }
    }

    inner class AndroidFiles {

        @JavascriptInterface
        fun savePdfForSharing(fileName: String, mimeType: String, base64: String): String {
            var createdUri: Uri? = null
            return try {
                val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
                    .take(120).ifBlank { "documento_firmado.pdf" }
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/FirmaPDF")
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("No se pudo crear el PDF")
                createdUri = uri
                val outputStream = contentResolver.openOutputStream(uri)
                    ?: throw IllegalStateException("No se pudo escribir el PDF")
                outputStream.use { it.write(bytes) }
                lastSavedPdfUri = uri
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "PDF listo para compartir: " + safeName,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                uri.toString()
            } catch (e: Exception) {
                createdUri?.let { uri -> try { contentResolver.delete(uri, null, null) } catch (_: Exception) {} }
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Error al preparar el PDF para compartir: " + (e.message ?: "desconocido"),
                        Toast.LENGTH_LONG
                    ).show()
                }
                ""
            }
        }

        @JavascriptInterface
        fun shareLastSavedPdf() {
            val uri = lastSavedPdfUri
            if (uri == null) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Primero genera y guarda el PDF firmado.", Toast.LENGTH_LONG).show()
                }
                return
            }
            runOnUiThread {
                try {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = ClipData.newUri(contentResolver, "PDF firmado", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(sendIntent, "Enviar PDF firmado con"))
                } catch (e: Exception) {
                    Toast.makeText(
                        this@MainActivity,
                        "No se pudo abrir el menú para compartir: " + (e.message ?: "desconocido"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        @JavascriptInterface
        fun saveBase64(fileName: String, mimeType: String, base64: String) {
            runOnUiThread {
                try {
                    val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
                        .take(120).ifBlank { "documento_firmado.pdf" }
                    val bytes = Base64.decode(base64, Base64.DEFAULT)
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                        put(MediaStore.Downloads.MIME_TYPE, mimeType)
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/FirmaPDF")
                    }
                    val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: throw IllegalStateException("No se pudo crear el archivo")
                    contentResolver.openOutputStream(uri).use { stream: OutputStream? ->
                        if (stream == null) throw IllegalStateException("No se pudo abrir el archivo")
                        stream.write(bytes)
                    }
                    Toast.makeText(
                        this@MainActivity,
                        "Guardado en Descargas/FirmaPDF: " + safeName,
                        Toast.LENGTH_LONG
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(
                        this@MainActivity,
                        "Error al guardar el archivo: " + (e.message ?: "desconocido"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    @Deprecated("Deprecated Android API retained for device compatibility")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) webView.destroy()
        super.onDestroy()
    }
}
