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
import androidx.appcompat.app.AppCompatActivity
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

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}