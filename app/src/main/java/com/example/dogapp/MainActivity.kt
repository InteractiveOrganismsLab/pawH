package com.example.dogapp

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.dogapp.databinding.ActivityMainBinding
import com.nixsensor.universalsdk.IDeviceScanner

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    companion object {
        // Define a constant value of your choice here
        const val PERMISSION_REQUEST_BLUETOOTH = 1000
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar)

        binding.appBarMain.fab.setOnClickListener { view ->
            Snackbar.make(view, "Start testing", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
            // Get the NavController associated with the current Activity
            val navController = findNavController(R.id.nav_host_fragment_content_main)

            // Navigate to the action associated with navigating to CameraFragment
            navController.navigate(R.id.global_action_to_function) // Make sure this ID is correct
        }

        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_content_main)

        // Passing each menu ID as a set of Ids because each menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(
            setOf(R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow, R.id.nav_logout, R.id.nav_camera), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        checkAndRequestBluetoothPermissions()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    private fun checkAndRequestBluetoothPermissions() {
        if (!IDeviceScanner.isBluetoothPermissionGranted(this)) {
            IDeviceScanner.requestBluetoothPermissions(this, PERMISSION_REQUEST_BLUETOOTH)
        }
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // Check if all requested permissions have been granted
        var allGranted = true
        for (result in grantResults) allGranted =
            allGranted and (result == PackageManager.PERMISSION_GRANTED)

        when (requestCode) {
            PERMISSION_REQUEST_BLUETOOTH -> {
                if (allGranted) {
                    // All permissions granted, OK to use `DeviceScanner`
                    // ...
                } else {
                    // Handle permission denial
                    // ...
                }
            }
        }
    }
}
