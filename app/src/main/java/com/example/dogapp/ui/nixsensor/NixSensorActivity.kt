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
import com.nixsensor.universalsdk.IMeasurementData
import com.nixsensor.universalsdk.OnDeviceResultListener
import com.nixsensor.universalsdk.ReferenceWhite
import com.nixsensor.universalsdk.ScanMode

class NixSensorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityNixsensorBinding
    private var savedImageUri: Uri? = null
    private var recalledDevice: IDeviceCompat? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize binding
        binding = ActivityNixsensorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize and start the device scanner
        //initializeScanner()

        // Recall Device
        recallDevice()

        // Set click listener for the send button
        binding.buttonS.setOnClickListener {
            Toast.makeText(this, "measure", Toast.LENGTH_SHORT).show()
            sensorMeasure()
        }

        binding.buttonH.setOnClickListener {
            navigateToHome()
        }
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

    // Recall the known Nix device using its ID and Name
    private fun recallDevice() {
        val context: Context = applicationContext
        val deviceId = "CB:1A:6C:5A:7F:82"  // The device ID obtained during scanning
        val deviceName = "Nix Spectro 2"   // The device name obtained during scanning

        // Create the IDeviceCompat instance
        recalledDevice = DeviceCompat(context, deviceId, deviceName)

        Log.d(TAG, "Recalled device: $deviceName with ID: $deviceId")

        // Proceed to open a connection
        openConnection()
    }

    // Open a connection to the recalled device
    private fun openConnection() {
        // Define the OnDeviceStateChangeListener
        val deviceStateListener = object : IDeviceCompat.OnDeviceStateChangeListener {
            override fun onConnected(sender: IDeviceCompat) {
                // Device has connected successfully
                Log.d(TAG, "Device connected: ${sender.name}")
                // Show a toast when the device is connected
                runOnUiThread {
                    Toast.makeText(this@NixSensorActivity, "Device connected: ${sender.name}", Toast.LENGTH_SHORT).show()
                }

                // You can now query the device and perform operations
            }

            override fun onDisconnected(sender: IDeviceCompat, status: DeviceStatus) {
                // Handle disconnection, check status codes
                Log.d(TAG, "Device disconnected: ${sender.name} with status: $status")

                when (status) {
                    DeviceStatus.ERROR_UNAUTHORIZED -> {
                        Log.e(TAG, "Device not authorized for this SDK build")
                    }
                    DeviceStatus.SUCCESS -> {
                        Log.d(TAG, "Normal disconnect")
                    }
                    DeviceStatus.ERROR_DROPPED_CONNECTION -> {
                        Log.e(TAG, "Device dropped the connection")
                    }
                    DeviceStatus.ERROR_TIMEOUT -> {
                        Log.e(TAG, "Connection to device timed out")
                    }
                    else -> {
                        Log.e(TAG, "Other connection error: $status")
                    }
                }
            }

            override fun onBatteryStateChanged(sender: IDeviceCompat, newState: Int) {
                // Handle battery state changes if needed
                Log.d(TAG, "Battery state changed: $newState")
            }

            override fun onExtPowerStateChanged(sender: IDeviceCompat, newState: Boolean) {
                // Handle external power state changes if needed
                Log.d(TAG, "External power state changed: $newState")
            }
        }

        // Connect to the recalled device
        recalledDevice?.connect(deviceStateListener)
    }

    // Define callback for measurement
    private val measureListener = object : OnDeviceResultListener {
        override fun onDeviceResult(
            status: CommandStatus,
            measurements: Map<ScanMode, IMeasurementData>?
        ) {
            when (status) {
                CommandStatus.SUCCESS -> {
                    // Successful operation
                    measurements?.let {
                        // Assuming you're using a specific ScanMode, e.g., ScanMode.M2
                        val measurementData = it[ScanMode.M2]
                        val referenceWhite = ReferenceWhite.D50_2
                        measurementData?.let { data ->
                            if (data.providesColor(referenceWhite)) {
                                val colorData = data.toColorData(referenceWhite)
                                colorData?.let { color ->
                                    val rgbValue = color.rgbValue
                                    val rgbString = "RGB: (${rgbValue[0]}, ${rgbValue[1]}, ${rgbValue[2]})"
                                    Log.d(TAG, rgbString)

                                    // Show the RGB value on a TextView and set the color of the square
                                    runOnUiThread {
                                        binding.rgbTextView.text = rgbString
                                        binding.rgbTextView.visibility = View.VISIBLE
                                        val colorInt = Color.rgb(rgbValue[0], rgbValue[1], rgbValue[2])
                                        binding.colorSquare.setBackgroundColor(colorInt)
                                        binding.colorSquare.visibility = View.VISIBLE
                                    }
                                } ?: run {
                                    Log.e(TAG, "Failed to obtain IColorData")
                                }
                            } else {
                                Log.e(TAG, "Color data not available for this reference white")
                            }
                        } ?: run {
                            Log.e(TAG, "No measurement data available")
                        }
                    }
                }
                CommandStatus.ERROR_NOT_READY -> {
                    // Did not complete because the device was busy
                }
                CommandStatus.ERROR_NOT_SUPPORTED -> {
                    // Did not complete because an unsupported scan mode was specified
                }
                CommandStatus.ERROR_LOW_POWER -> {
                    // Did not complete because the battery level is too low
                }
                CommandStatus.ERROR_TIMEOUT -> {
                    // Timeout when waiting for result
                }
                CommandStatus.ERROR_AMBIENT_LIGHT -> {
                    // Did not complete because of ambient light leakage
                }
                else -> {
                    // Did not complete because of other internal error
                }
            }
        }
    }


    private fun sensorMeasure(){
        recalledDevice?.let {
            it.measure(measureListener)
        } ?: run {
            Log.e(TAG, "Device is not connected or initialized")
        }
    }

    private fun navigateToHome() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
    }
}
