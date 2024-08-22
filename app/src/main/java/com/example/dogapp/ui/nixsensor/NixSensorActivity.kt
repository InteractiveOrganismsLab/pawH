package com.example.dogapp.ui.nixsensor

import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.dogapp.databinding.ActivityNixsensorBinding
import com.example.dogapp.network.MarsApi
import kotlinx.coroutines.launch
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.lang.Exception
import com.example.dogapp.MainActivity
import com.nixsensor.universalsdk.CommandStatus
import com.nixsensor.universalsdk.DeviceCompat
import com.nixsensor.universalsdk.IDeviceCompat
import com.nixsensor.universalsdk.IDeviceScanner
import com.nixsensor.universalsdk.DeviceScanner
import com.nixsensor.universalsdk.DeviceStatus
import com.nixsensor.universalsdk.IColorData
import com.nixsensor.universalsdk.IMeasurementData
import com.nixsensor.universalsdk.OnDeviceResultListener
import com.nixsensor.universalsdk.ReferenceWhite
import com.nixsensor.universalsdk.ScanMode
import kotlin.math.pow
import kotlin.math.sqrt
class NixSensorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNixsensorBinding
    private var recalledDevice: IDeviceCompat? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNixsensorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        recallDevice()
    }

    private fun setupUI() {
        binding.buttonS.setOnClickListener { measureSensorData() }
        binding.buttonH.setOnClickListener { navigateToHome() }
    }

    private fun recallDevice() {
        val deviceId = "CB:1A:6C:5A:7F:82"
        val deviceName = "Nix Spectro 2"

        recalledDevice = DeviceCompat(applicationContext, deviceId, deviceName)
        connectToDevice()
    }

    private fun connectToDevice() {
        recalledDevice?.connect(object : IDeviceCompat.OnDeviceStateChangeListener {
            override fun onConnected(sender: IDeviceCompat) {
                showToast("Connected to ${sender.name}")
                updateUIOnConnection()
            }

            override fun onDisconnected(sender: IDeviceCompat, status: DeviceStatus) {
                handleDisconnection(sender, status)
            }

            override fun onBatteryStateChanged(sender: IDeviceCompat, newState: Int) {
                Log.d("NixSensorActivity", "Battery state: $newState")
            }

            override fun onExtPowerStateChanged(sender: IDeviceCompat, newState: Boolean) {
                Log.d("NixSensorActivity", "External power state: $newState")
            }
        }) ?: Log.e("NixSensorActivity", "Recalled device is null, connection failed.")
    }

    private fun updateUIOnConnection() {
        binding.rgbTextView.visibility = View.VISIBLE
        binding.colorSquare.visibility = View.VISIBLE
    }

    private fun measureSensorData() {
        recalledDevice?.measure(object : OnDeviceResultListener {
            override fun onDeviceResult(status: CommandStatus, measurements: Map<ScanMode, IMeasurementData>?) {
                if (status == CommandStatus.SUCCESS) {
                    measurements?.get(ScanMode.M2)?.let { measurementData ->
                        val colorData = measurementData.toColorData(ReferenceWhite.D50_2)
                        updateColorDisplay(colorData)
                        val predictedPH =  predictPH(colorData?.rgbValue ?: intArrayOf(0, 0, 0))
                        updatePHDisplay(predictedPH)
                    } ?: Log.e("NixSensorActivity", "Measurement data unavailable.")
                } else {
                    logMeasurementError(status)
                }
            }
        }) ?: Log.e("NixSensorActivity", "Device is not connected.")
    }

    private fun updatePHDisplay(predictedPH: String) {
        runOnUiThread {
            binding.predictionsTextView.text = "pH: $predictedPH"
        }
    }

    private fun predictPH(rgbValue: IntArray): String {
        val nearestLab = mapOf(
            "6.0" to intArrayOf(79, 72, 103),
            "7.0" to intArrayOf(93, 96, 114),
            "8.0" to intArrayOf(86, 102, 105),
            "9.0" to intArrayOf(71, 92, 86)
        )

        return nearestLab.minByOrNull { (_, value) ->
            euclideanDistance(rgbValue, value)
        }?.key ?: "Unknown"
    }

    private fun euclideanDistance(rgb1: IntArray, rgb2: IntArray): Double {
        return sqrt(
            (rgb1[0] - rgb2[0]).toDouble().pow(2) +
                    (rgb1[1] - rgb2[1]).toDouble().pow(2) +
                    (rgb1[2] - rgb2[2]).toDouble().pow(2)
        )
    }

    private fun updateColorDisplay(colorData: IColorData?) {
        colorData?.let {
            val rgb = it.rgbValue
            runOnUiThread {
                binding.rgbTextView.text = "RGB: (${rgb[0]}, ${rgb[1]}, ${rgb[2]})"
                binding.colorSquare.setBackgroundColor(Color.rgb(rgb[0], rgb[1], rgb[2]))
            }
        }
    }

    private fun logMeasurementError(status: CommandStatus) {
        when (status) {
            CommandStatus.ERROR_NOT_READY -> Log.e("NixSensorActivity", "Device not ready.")
            CommandStatus.ERROR_LOW_POWER -> Log.e("NixSensorActivity", "Low battery power.")
            else -> Log.e("NixSensorActivity", "Measurement error: $status")
        }
    }

    private fun handleDisconnection(sender: IDeviceCompat, status: DeviceStatus) {
        val message = "Disconnected: ${sender.name} Status: $status"
        Log.d("NixSensorActivity", message)
        showToast(message)
    }

    private fun initializeScanner() {
        // Define the OnScannerStateChangeListener
        val scannerStateListener = object : IDeviceScanner.OnScannerStateChangeListener {
            override fun onScannerStarted(sender: IDeviceScanner) {
                // Scanner has started ...
                Log.d(TAG, "Scanner started")
            }

            override fun onScannerStopped(sender: IDeviceScanner) {
                // Scanner has stopped ...
                Log.d(TAG, "Scanner stopped")
            }
        }

        // Define the OnDeviceFoundListener
        val deviceFoundListener = object : IDeviceScanner.OnDeviceFoundListener {
            override fun onScanResult(sender: IDeviceScanner, device: IDeviceCompat) {
                // Nearby device found
                // Handle discovery here ...

                // Valid to query some parameters now:
                Log.d(TAG, String.format(
                    "Found %s (%s) at RSSI %d",
                    device.id,
                    device.name,
                    device.rssi)
                )
            }
        }

        // Application context
        val context: Context = applicationContext

        // Initialize the scanner
        val scanner = DeviceScanner(context)
        scanner.setOnScannerStateChangeListener(scannerStateListener)

        // Start the scanner
        scanner.start(listener = deviceFoundListener)
    }

    private fun navigateToHome() {
        startActivity(Intent(this, MainActivity::class.java))
    }

    override fun onDestroy() {
        recalledDevice?.disconnect()
        super.onDestroy()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}