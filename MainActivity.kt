package com.example.camerafr

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Environment
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.concurrent.fixedRateTimer

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var videoCapture: VideoCapture<Recorder>
    private var recording: androidx.camera.video.Recording? = null
    private val locationData = mutableListOf<Pair<Double, Double>>()
    private var timer: java.util.Timer? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        val btnRecord = findViewById<ImageButton>(R.id.Record)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Verifica los permisos necesarios
        if (!hasPermissions()) {
            requestPermissions()
        } else {
            startCamera()
        }

        btnRecord.setOnClickListener { toggleRecording(btnRecord) }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = androidx.camera.core.Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val recorder = Recorder.Builder()
                .setQualitySelector(androidx.camera.video.QualitySelector.from(androidx.camera.video.Quality.HD))
                .build()

            videoCapture = VideoCapture.withOutput(recorder)

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, videoCapture
                )
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Error al iniciar la cámara", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun toggleRecording(btnRecord: ImageButton) {
        if (recording == null) {
            startRecording()
            btnRecord.setImageResource(R.drawable.ic_stop)
        } else {
            stopRecording()
            btnRecord.setImageResource(R.drawable.ic_start)
        }
    }

    private fun startRecording() {
        val videoFile = File(
            getExternalFilesDir(Environment.DIRECTORY_MOVIES),
            "video_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())}.mp4"
        )

        val outputOptions = androidx.camera.video.FileOutputOptions.Builder(videoFile).build()

        recording = videoCapture.output.prepareRecording(this, outputOptions)
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                if (recordEvent is androidx.camera.video.VideoRecordEvent.Start) {
                    Toast.makeText(this, "Grabación iniciada", Toast.LENGTH_SHORT).show()
                    startLocationUpdates()
                }
            }
    }

    private fun stopRecording() {
        recording?.stop()
        recording = null
        stopLocationUpdates()
        saveLocationDataToText()
    }

    private fun startLocationUpdates() {
        timer = fixedRateTimer("LocationTimer", initialDelay = 0, period = 1000) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    val latitude = location.latitude
                    val longitude = location.longitude
                    locationData.add(Pair(latitude, longitude))
                } else {
                    Toast.makeText(this@MainActivity, "No se pudo obtener ubicación", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun stopLocationUpdates() {
        timer?.cancel()
        timer = null
    }

    private fun saveLocationDataToText() {
        val textFile = File(
            getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            "ubicaciones_${System.currentTimeMillis()}.txt"
        )

        try {
            FileWriter(textFile).use { writer ->
                locationData.forEachIndexed { index, pair ->
                    writer.write("Segundo ${index + 1}: Latitud = ${pair.first}, Longitud = ${pair.second}\n")
                }
            }
            Toast.makeText(this, "Ubicaciones guardadas en: ${textFile.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error al guardar archivo de texto", Toast.LENGTH_SHORT).show()
        }

        Toast.makeText(this, "Grabación detenida", Toast.LENGTH_SHORT).show()
    }

    private fun hasPermissions(): Boolean {
        val requiredPermissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            ),
            101
        )
    }
}
