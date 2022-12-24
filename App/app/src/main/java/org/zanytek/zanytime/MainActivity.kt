package org.zanytek.zanytime

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.work.*
import kotlinx.coroutines.*
import org.zanytek.zanytime.databinding.ActivityMainBinding
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.log

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val healthConnectPermissions = setOf(
        HealthPermission.createReadPermission(StepsRecord::class),
    )
    private val requestHealthConnectPermissions =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
            if (granted.containsAll(healthConnectPermissions)) {
                Log.i("Zanytime", "All necessary permissions granted after asking connect api")
            } else {
                Log.i(
                    "Zanytime",
                    "Necessary permissions not granted, even after asking connect api"
                )
            }
        }

    fun doWorkOnce(view: View) {
        val workManager = WorkManager.getInstance(this)
        val worker = OneTimeWorkRequestBuilder<SendDataWorker>().build()
        workManager.enqueue(worker)
    }

    fun getLastUpdateTime(view: View) {
        val prefs = getSharedPreferences("LastUpdate", Service.MODE_PRIVATE)
        val lastUpdate = prefs.getString("UpdateTime", "None")
        Toast.makeText(this, lastUpdate, Toast.LENGTH_SHORT).show()
    }

    fun startPeriodicWorker(view: View) {
        stopPeriodicWorker(view)

        val updateWatchRequest =
            PeriodicWorkRequestBuilder<SendDataWorker>(15, TimeUnit.MINUTES)
                .addTag("UpdateWatch")
                .build()

        WorkManager.getInstance(this).enqueue(updateWatchRequest)
        Log.i("MainAct", "Enqueued periodic work request")
    }

    fun stopPeriodicWorker(view: View) {
        WorkManager.getInstance(this).cancelAllWorkByTag("UpdateWatch")
    }

    fun requestGoogleHealthConnectPermissions(view: View) {
        val client = HealthConnectClient.getOrCreate(this)

        lifecycleScope.launch {
            val granted =
                client.permissionController.getGrantedPermissions(healthConnectPermissions)
            if (granted.containsAll(healthConnectPermissions)) {
                Log.i("Zanytime", "All necessary permissions already granted")
            } else {
                Log.i("Zanytime", "Currently don't have all permissions, launching request")
                requestHealthConnectPermissions.launch(healthConnectPermissions)
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}