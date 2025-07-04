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

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var videoCapture: VideoCapture<Recorder>
    private var recording: androidx.camera.video.Recording? = null
    private val locationData = mutableListOf<Pair<Double, Double>>()

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        val btnRecord = findViewById<ImageButton>(R.id.Record)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

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
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, videoCapture)
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
        Toast.makeText(this, "Grabación detenida", Toast.LENGTH_SHORT).show()
    }

    private fun startLocationUpdates() {
        locationData.clear()

        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(1000L)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    val lat = location.latitude
                    val lon = location.longitude
                    locationData.add(Pair(lat, lon))
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            mainLooper
        )
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
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

    // 👇 Este método es necesario para manejar correctamente la primera vez que se piden permisos
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startCamera()
            } else {
                Toast.makeText(this, "Se requieren todos los permisos para usar la app", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
