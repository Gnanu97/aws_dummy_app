package com.example.aws_dummy_app.activities

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.aws_dummy_app.databinding.ActivityGenerateQrBinding
import com.example.aws_dummy_app.network.RetrofitClient
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class GenerateQRActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGenerateQrBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGenerateQrBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnGenerateQR.setOnClickListener {
            generateToken()
        }

        binding.btnShareQR.setOnClickListener {
            shareQRCode()
        }
    }

    private fun generateToken() {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnGenerateQR.isEnabled = false
        binding.btnShareQR.visibility = View.GONE
        binding.ivQrCode.visibility = View.GONE
        binding.tvTokenInfo.visibility = View.GONE
        binding.tvStatus.text = "⏳ Generating secure token..."

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.qrApiService.generateToken()
                }

                if (response.isSuccessful) {
                    val token = response.body()?.token ?: ""

                    if (token.isNotEmpty()) {
                        val qrBitmap = generateQRBitmap(token)

                        binding.progressBar.visibility = View.GONE
                        binding.btnGenerateQR.isEnabled = true

                        if (qrBitmap != null) {
                            binding.ivQrCode.setImageBitmap(qrBitmap)
                            binding.ivQrCode.visibility = View.VISIBLE
                            binding.tvTokenInfo.text = "🔑 Token: ${token.take(8)}...  (valid 5 mins)"
                            binding.tvTokenInfo.visibility = View.VISIBLE
                            binding.tvStatus.text = "✅ QR Ready — Show to delivery agent"
                            // Show share button only after QR is ready
                            binding.btnShareQR.visibility = View.VISIBLE
                        }
                    }
                } else {
                    showError("Server error: ${response.code()}")
                }

            } catch (e: Exception) {
                showError("Network error: ${e.message}")
            }
        }
    }

    private fun shareQRCode() {
        val bitmap = (binding.ivQrCode.drawable as? BitmapDrawable)?.bitmap
        if (bitmap == null) {
            Toast.makeText(this, "No QR code to share", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Save bitmap to app cache directory
            val cachePath = File(cacheDir, "qr_codes")
            cachePath.mkdirs()
            val file = File(cachePath, "door_access_qr.png")
            FileOutputStream(file).use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }

            // Get shareable URI via FileProvider
            val uri: Uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )

            // Launch Android share sheet (WhatsApp, Gmail, Drive, etc.)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "🔑 Door Access QR Code — Valid for 5 minutes")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share QR Code via"))

        } catch (e: Exception) {
            Toast.makeText(this, "Failed to share: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun generateQRBitmap(token: String): Bitmap? {
        return try {
            val multiFormatWriter = MultiFormatWriter()
            val bitMatrix = multiFormatWriter.encode(
                token,
                BarcodeFormat.QR_CODE,
                600, 600
            )
            val barcodeEncoder = BarcodeEncoder()
            barcodeEncoder.createBitmap(bitMatrix)
        } catch (e: Exception) {
            null
        }
    }

    private fun showError(msg: String) {
        binding.progressBar.visibility = View.GONE
        binding.btnGenerateQR.isEnabled = true
        binding.btnShareQR.visibility = View.GONE
        binding.tvStatus.text = "❌ $msg"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}