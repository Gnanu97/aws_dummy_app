package com.example.aws_dummy_app.activities

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.aws_dummy_app.databinding.ActivityQrValidatorBinding
import com.example.aws_dummy_app.network.RetrofitClient
import com.example.aws_dummy_app.network.ValidateTokenRequest
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@OptIn(androidx.camera.core.ExperimentalGetImage::class)
class QRValidatorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQrValidatorBinding
    private lateinit var cameraExecutor: ExecutorService
    private var scanned = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQrValidatorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()
        startCamera()

        binding.btnRescan.setOnClickListener {
            resetScanner()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                processImageProxy(imageProxy)
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Camera error: ${e.message}", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        if (scanned) { imageProxy.close(); return }

        val mediaImage = imageProxy.image ?: run { imageProxy.close(); return }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()

        BarcodeScanning.getClient(options).process(image)
            .addOnSuccessListener { barcodes ->
                if (barcodes.isNotEmpty() && !scanned) {
                    val token = barcodes[0].rawValue ?: ""
                    if (token.isNotEmpty()) {
                        scanned = true
                        validateToken(token)
                    }
                }
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    private fun validateToken(token: String) {
        runOnUiThread {
            binding.previewView.visibility = View.GONE
            binding.progressBar.visibility = View.VISIBLE
            binding.tvStatus.text = "⏳ Validating token..."
            binding.cardResult.visibility = View.GONE
            binding.btnRescan.visibility = View.GONE
        }

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.qrApiService.validateToken(ValidateTokenRequest(token))
                }

                runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    binding.cardResult.visibility = View.VISIBLE

                    if (response.isSuccessful) {
                        // ✅ 200 — Lambda returns {"message": "Token valid"}
                        val message = response.body()?.message ?: ""

                        when {
                            message.equals("Token valid", ignoreCase = true) -> {
                                binding.tvStatus.text = "✅ Access Granted"
                                binding.tvResult.text = "Door Unlocked!\nWelcome 🎉"
                                binding.cardResult.setCardBackgroundColor(
                                    getColor(android.R.color.holo_green_light)
                                )
                                binding.btnRescan.visibility = View.GONE
                            }
                            else -> {
                                binding.tvStatus.text = "❌ Unknown Response"
                                binding.tvResult.text = "Unexpected response.\nPlease try again."
                                binding.cardResult.setCardBackgroundColor(
                                    getColor(android.R.color.holo_red_light)
                                )
                                binding.btnRescan.visibility = View.VISIBLE
                            }
                        }
                    } else {
                        // ❌ 400/500 — body() is null, must read errorBody()
                        val errorMessage = try {
                            val errorJson = response.errorBody()?.string() ?: ""
                            JSONObject(errorJson).getString("message")
                        } catch (e: Exception) {
                            "Unknown error"
                        }

                        when {
                            errorMessage.contains("expired", ignoreCase = true) -> {
                                binding.tvStatus.text = "⏰ Token Expired"
                                binding.tvResult.text = "QR code has expired.\nPlease generate a new one."
                                binding.cardResult.setCardBackgroundColor(
                                    getColor(android.R.color.holo_orange_light)
                                )
                            }
                            errorMessage.contains("already used", ignoreCase = true) -> {
                                binding.tvStatus.text = "⚠️ Already Used"
                                binding.tvResult.text = "This QR was already scanned.\nPlease generate a new one."
                                binding.cardResult.setCardBackgroundColor(
                                    getColor(android.R.color.holo_orange_light)
                                )
                            }
                            errorMessage.contains("invalid", ignoreCase = true) -> {
                                binding.tvStatus.text = "❌ Access Denied"
                                binding.tvResult.text = "Invalid QR code.\nPlease try again."
                                binding.cardResult.setCardBackgroundColor(
                                    getColor(android.R.color.holo_red_light)
                                )
                            }
                            else -> {
                                binding.tvStatus.text = "❌ Error ${response.code()}"
                                binding.tvResult.text = errorMessage
                                binding.cardResult.setCardBackgroundColor(
                                    getColor(android.R.color.holo_red_light)
                                )
                            }
                        }
                        binding.btnRescan.visibility = View.VISIBLE
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    binding.cardResult.visibility = View.VISIBLE
                    binding.tvStatus.text = "❌ Network Error"
                    binding.tvResult.text = "Check your internet connection.\n${e.message}"
                    binding.cardResult.setCardBackgroundColor(
                        getColor(android.R.color.holo_red_light)
                    )
                    binding.btnRescan.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun resetScanner() {
        scanned = false
        binding.previewView.visibility = View.VISIBLE
        binding.cardResult.visibility = View.GONE
        binding.btnRescan.visibility = View.GONE
        binding.tvStatus.text = "Point camera at QR code"
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
