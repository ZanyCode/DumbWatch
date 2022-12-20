package org.zanytek.zanytime

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.work.*
import kotlinx.coroutines.*
import org.zanytek.zanytime.databinding.ActivityMain2Binding
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.log

private const val ENABLE_BLUETOOTH_REQUEST_CODE = 1


class MainActivity2 : AppCompatActivity() {
    private val defaultScope = CoroutineScope(Dispatchers.Default)
    private var gattServiceConn: MainActivity2.GattServiceConn? = null
    private var gattServiceData: GattService.DataPlane? = null
    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }
    private lateinit var binding: ActivityMain2Binding

    fun startService(view: View) {
        stopService(view)
        // Startup our Bluetooth GATT service explicitly so it continues to run even if
        // this activity is not in focus
        startForegroundService(Intent(this, GattService::class.java))

        val latestGattServiceConn = GattServiceConn()
        if (bindService(
                Intent(GattService.DATA_PLANE_ACTION, null, this, GattService::class.java),
                latestGattServiceConn,
                0
            )
        ) {
            gattServiceConn = latestGattServiceConn
        }
    }

    fun stopService(view: View) {
        stopService(Intent(this, GattService::class.java))
    }

    fun connect(view: View) {
        defaultScope.launch {
            gattServiceData?.connectToWatch()
        }
    }

    fun disconnect(view: View) {
        gattServiceData?.disconnectWatch()
    }

    fun doWorkOnce(view: View) {
        val workManager = WorkManager.getInstance(this)
        val emotionAnalysisWorker = OneTimeWorkRequestBuilder<SendDataWorker>().build()
        workManager.enqueue(emotionAnalysisWorker)
    }

    fun startPeriodicWorker(view: View) {
        stopPeriodicWorker(view)

        val updateWatchRequest =
            PeriodicWorkRequestBuilder<SendDataWorker>(15, TimeUnit.MINUTES)
                .addTag("UpdateWatch")
                .build()

        WorkManager.getInstance(this).enqueue(updateWatchRequest)
        Log.i("MainAct2", "Enqueued periodic work request")
    }

    fun stopPeriodicWorker(view: View) {
        WorkManager.getInstance(this).cancelAllWorkByTag("UpdateWatch")
    }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMain2Binding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    override fun onStop() {
        super.onStop()

        if (gattServiceConn != null) {
            unbindService(gattServiceConn!!)
            gattServiceConn = null
        }
    }

    override fun onStart() {
        super.onStart()

        val latestGattServiceConn = GattServiceConn()
        if (bindService(
                Intent(GattService.DATA_PLANE_ACTION, null, this, GattService::class.java),
                latestGattServiceConn,
                0
            )
        ) {
            gattServiceConn = latestGattServiceConn
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // We only want the service around for as long as our app is being run on the device
//        stopService(Intent(this, GattService::class.java))
    }

    private inner class GattServiceConn : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName?) {
            if (BuildConfig.DEBUG && GattService::class.java.name != name?.className) {
                error("Disconnected from unknown service")
            } else {
                gattServiceData = null
            }
        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (BuildConfig.DEBUG && GattService::class.java.name != name?.className)
                error("Connected to unknown service")
            else {
                gattServiceData = service as GattService.DataPlane
            }
        }
    }
}