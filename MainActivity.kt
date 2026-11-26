package com.aryandigital.docclean

import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.InputType
import android.text.StaticLayout
import android.text.TextPaint
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val navy = Color.parseColor("#0B1F4B")
    private val blue = Color.parseColor("#1565FF")
    private val page = Color.parseColor("#F4F7FF")

    private var hindi = false
    private var scanPdf: Uri? = null

    private lateinit var btnEn: Button
    private lateinit var btnHi: Button
    private lateinit var btnScan: Button
    private lateinit var progress: ProgressBar
    private lateinit var resultBox: LinearLayout
    private lateinit var paper: EditText

    private val scanLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { res ->
        if (res.resultCode == RESULT_OK) {
            val r = GmsDocumentScanningResult.fromActivityResultIntent(res.data)
            scanPdf = r?.pdf?.uri
            val pages = r?.pages?.map { it.imageUri } ?: emptyList()
            if (pages.isNotEmpty()) recognize(pages)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
    }

    // ---------- UI ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun button(label: String, primary: Boolean, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 16f
            setTextColor(if (primary) Color.WHITE else navy)
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(if (primary) blue else Color.WHITE)
                if (!primary) setStroke(dp(2), blue)
            }
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
            ).apply { topMargin = dp(10) }
        }

    private fun buildUi(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(24))
        }

        col.addView(ImageView(this).apply {
            setImageResource(R.drawable.logo)
            adjustViewBounds = true
            contentDescription = "DocClean"
            layoutParams = LinearLayout.LayoutParams(dp(240), ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { gravity = Gravity.CENTER_HORIZONTAL }
        })

        col.addView(TextView(this).apply {
            text = "Language of the writing / लिखावट की भाषा"
            setTextColor(navy)
            textSize = 14f
            setPadding(0, dp(12), 0, dp(6))
        })

        val langRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        btnEn = Button(this).apply { text = "English"; isAllCaps = false; setOnClickListener { setLang(false) } }
        btnHi = Button(this).apply { text = "हिंदी"; isAllCaps = false; setOnClickListener { setLang(true) } }
        val half = { LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(6); marginStart = dp(6) } }
        langRow.addView(btnEn, half())
        langRow.addView(btnHi, half())
        col.addView(langRow)
        setLang(false)

        btnScan = button("Scan document", true) { startScan() }
        col.addView(btnScan)

        progress = ProgressBar(this).apply { visibility = View.GONE }
        col.addView(progress, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(12) })

        resultBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        paper = EditText(this).apply {
            hint = "Your clean text will appear here"
            setTextColor(Color.BLACK)
            textSize = 16f
            gravity = Gravity.TOP or Gravity.START
            minLines = 8
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(Color.WHITE)
                setStroke(dp(1), Color.parseColor("#CBD5E1"))
            }
        }
        resultBox.addView(paper, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(16) })
        resultBox.addView(button("Download PDF (white paper)", true) { savePdf() })
        resultBox.addView(button("Download TXT", false) { saveTxt() })
        resultBox.addView(button("Save straightened scan (PDF)", false) { saveScan() })
        resultBox.addView(button("Share text", false) { shareText() })
        col.addView(resultBox)

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(navy) }
        }
        footer.addView(TextView(this).apply {
            text = "Created by Aryan Digital Solutions\nAlways free. Want your own app built?"
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            textSize = 14f
        })
        footer.addView(Button(this).apply {
            text = "Message us on WhatsApp"
            isAllCaps = false
            setTextColor(navy)
            background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(Color.WHITE) }
            setOnClickListener { openWhatsApp() }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(48)
        ).apply { topMargin = dp(10) })
        col.addView(footer, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(24) })

        val scroll = ScrollView(this).apply {
            setBackgroundColor(page)
            clipToPadding = false
            addView(col)
        }
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val b = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            v.setPadding(b.left, b.top, b.right, b.bottom)
            WindowInsetsCompat.CONSUMED
        }
        return scroll
    }

    private fun setLang(isHindi: Boolean) {
        hindi = isHindi
        fun style(b: Button, on: Boolean) {
            b.setTextColor(if (on) Color.WHITE else navy)
            b.background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(if (on) blue else Color.WHITE)
                setStroke(dp(2), blue)
            }
        }
        style(btnEn, !isHindi)
        style(btnHi, isHindi)
    }

    private fun busy(on: Boolean) {
        progress.visibility = if (on) View.VISIBLE else View.GONE
        btnScan.isEnabled = !on
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    // ---------- Scan + OCR ----------

    private fun startScan() {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(5)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF
            )
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(this)
            .addOnSuccessListener { scanLauncher.launch(IntentSenderRequest.Builder(it).build()) }
            .addOnFailureListener {
                toast("Scanner is not available. Update Google Play services and try again.")
            }
    }

    private fun recognize(pages: List<Uri>) {
        busy(true)
        val rec = if (hindi) {
            TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
        } else {
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        }
        val sb = StringBuilder()

        fun fail() {
            rec.close()
            busy(false)
            toast("Could not read the text. Check your internet connection (needed once to download the reading model) and try again.")
        }

        fun next(i: Int) {
            if (i >= pages.size) {
                rec.close()
                busy(false)
                val out = sb.toString().trim()
                if (out.isEmpty()) {
                    toast("No text found. Try again in good light with dark ink.")
                } else {
                    paper.setText(out)
                    resultBox.visibility = View.VISIBLE
                }
                return
            }
            try {
                rec.process(InputImage.fromFilePath(this, pages[i]))
                    .addOnSuccessListener { sb.append(it.text).append("\n\n"); next(i + 1) }
                    .addOnFailureListener { fail() }
            } catch (e: Exception) {
                fail()
            }
        }
        next(0)
    }

    // ---------- Save / share ----------

    private fun stamp() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    private fun saveToDownloads(name: String, mime: String, write: (OutputStream) -> Unit): Boolean {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return false
            contentResolver.openOutputStream(uri)?.use(write) ?: return false
            toast("Saved in Downloads: $name")
            true
        } catch (e: Exception) {
            toast("Could not save the file.")
            false
        }
    }

    private fun saveTxt() {
        val text = paper.text.toString()
        saveToDownloads("DocClean_${stamp()}.txt", "text/plain") { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    private fun saveScan() {
        val src = scanPdf
        if (src == null) { toast("No scan available."); return }
        saveToDownloads("DocClean_scan_${stamp()}.pdf", "application/pdf") { out ->
            contentResolver.openInputStream(src)?.use { it.copyTo(out) }
        }
    }

    private fun savePdf() {
        val text = paper.text.toString().trim()
        if (text.isEmpty()) { toast("Nothing to save."); return }
        val doc = buildTextPdf(text)
        saveToDownloads("DocClean_${stamp()}.pdf", "application/pdf") { doc.writeTo(it) }
        doc.close()
    }

    private fun buildTextPdf(text: String): PdfDocument {
        val pw = 595
        val ph = 842
        val margin = 48
        val bottom = 64
        val width = pw - 2 * margin
        val contentH = ph - margin - bottom

        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 13f }
        val foot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.GRAY; textSize = 9f }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, body, width)
            .setLineSpacing(4f, 1f)
            .build()

        val doc = PdfDocument()
        var line = 0
        var number = 1
        while (line < layout.lineCount) {
            val top = layout.getLineTop(line)
            var end = line
            while (end + 1 < layout.lineCount && layout.getLineBottom(end + 1) - top <= contentH) end++

            val p = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, number).create())
            val c = p.canvas
            c.drawText("Created with DocClean - Aryan Digital Solutions", margin.toFloat(), (ph - 28).toFloat(), foot)
            c.save()
            c.translate(margin.toFloat(), (margin - top).toFloat())
            c.clipRect(0f, top.toFloat(), width.toFloat(), layout.getLineBottom(end).toFloat())
            layout.draw(c)
            c.restore()
            doc.finishPage(p)

            line = end + 1
            number++
        }
        return doc
    }

    private fun shareText() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, paper.text.toString())
        }
        startActivity(Intent.createChooser(send, "Share text"))
    }

    private fun openWhatsApp() {
        val msg = Uri.encode("Hello Aryan Digital Solutions, I want an app built.")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/919128692966?text=$msg")))
        } catch (e: Exception) {
            toast("Please message +91 91286 92966")
        }
    }
}
