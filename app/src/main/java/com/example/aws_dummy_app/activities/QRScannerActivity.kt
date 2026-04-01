package com.example.aws_dummy_app.activities

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.aws_dummy_app.databinding.ActivityQrScannerBinding
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class QRScannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQrScannerBinding
    private lateinit var cameraExecutor: ExecutorService
    private var macAddress = ""
    private var scanned = false

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) startCamera()
        else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQrScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        macAddress = intent.getStringExtra("MAC_ADDRESS") ?: ""
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Preview — shows camera feed on screen
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            // Image Analysis — processes frames for QR detection
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
                Log.d("QR", "Camera bound successfully")
            } catch (e: Exception) {
                Log.e("QR", "Camera binding failed: ${e.message}")
                Toast.makeText(this, "Camera error: ${e.message}", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.camera.core.ExperimentalGetImage
    private fun processImageProxy(imageProxy: ImageProxy) {
        // If already scanned, just close and skip
        if (scanned) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        // Convert camera frame to ML Kit InputImage
        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        // Configure scanner to look for QR codes specifically
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()

        val scanner = BarcodeScanning.getClient(options)

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                if (barcodes.isNotEmpty() && !scanned) {
                    val rawValue = barcodes[0].rawValue ?: ""
                    Log.d("QR", "QR Scanned: $rawValue")

                    if (rawValue.isNotEmpty()) {
                        scanned = true
                        processQrResult(rawValue)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("QR", "Barcode scan failed: ${e.message}")
            }
            .addOnCompleteListener {
                // Always close the imageProxy when done
                imageProxy.close()
            }
    }

    private fun processQrResult(rawValue: String) {
        // Extract random_id from QR
        // Supports both formats:
        // Format 1: "random_id=ABC123"
        // Format 2: plain "ABC123"
        val randomId = when {
            rawValue.contains("random_id=") ->
                rawValue.substringAfter("random_id=").trim()
            rawValue.contains(",") ->
                rawValue.split(",").firstOrNull()?.trim() ?: rawValue.trim()
            else -> rawValue.trim()
        }

        Log.d("QR", "Extracted random_id: $randomId, MAC: $macAddress")

        runOnUiThread {
            Toast.makeText(this, "QR Scanned! ID: $randomId", Toast.LENGTH_SHORT).show()

            startActivity(Intent(this, RegistrationActivity::class.java).apply {
                putExtra("MAC_ADDRESS", macAddress)
                putExtra("RANDOM_ID", randomId)
            })
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
