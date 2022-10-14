package org.zanytek.zanytime

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.icu.text.SimpleDateFormat
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import java.util.*
import kotlin.system.measureTimeMillis
import kotlin.time.Duration

class BleDataPublisherService : Service() {
    var device: BluetoothDevice? = null
    var gatt: BluetoothGatt? = null
    var btleService: BtleService = BtleService(this)
    private val scope = CoroutineScope(newSingleThreadContext("name"))
    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    override fun onCreate() {
        super.onCreate()

        Log.d("BLEService", "onCreate")

        // Setup as a foreground service
        val notificationChannel = NotificationChannel(
            BleDataPublisherService::class.java.simpleName,
            resources.getString(R.string.gatt_service_name),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val notificationService =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationService.createNotificationChannel(notificationChannel)

        val notification = NotificationCompat.Builder(this, BleDataPublisherService::class.java.simpleName)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(resources.getString(R.string.gatt_service_name))
            .setContentText(resources.getString(R.string.gatt_service_running_notification))
            .setAutoCancel(true)

        startForeground(1, notification.build())

        scope.launch {
            while(true) {
                publishData()
                delay(56000)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun publishData() {
        if (device == null)
            device = btleService.getBtDevice(bluetoothAdapter, 10000, "80323644-3537-4f0b-a53b-cf494eceaab3")

        val elapsedMs = measureTimeMillis {
            gatt = btleService.getBtGatt(device!!, true)
            var charService =
                gatt!!.getService(UUID.fromString("80323644-3537-4f0b-a53b-cf494eceaab3"))
            var characteristic =
                charService.getCharacteristic(UUID.fromString("80323644-3537-4f0b-a53b-cf494eceaab3"))
            var currentDate = SimpleDateFormat("HH:mm:ss").format(Date())
            characteristic.value = currentDate.encodeToByteArray()
            gatt!!.writeCharacteristic(characteristic)
            gatt!!.disconnect()

        }

        Log.d("COroutine", "Done, took ${elapsedMs}mS")
    }

    override fun onBind(p0: Intent?): IBinder? {
        Log.d("BLEService", "onBind")
        return Binder()
    }

    override fun onDestroy() {
        Log.d("BLEService", "onDestroy")
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d("BLEService", "onUnbinf")
        return super.onUnbind(intent)
    }
}