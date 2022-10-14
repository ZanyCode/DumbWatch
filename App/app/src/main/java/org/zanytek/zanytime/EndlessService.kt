package org.zanytek.zanytime;

import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.*
import org.zanytek.zanytime.MainActivity2
import org.zanytek.zanytime.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.system.measureTimeMillis

class EndlessService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var isServiceStarted = false
    var device: BluetoothDevice? = null
    var gatt: BluetoothGatt? = null
    var btleService: BtleService = BtleService(this)
    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    fun log(msg: String) {
        Log.d("ENDLESS-SERVICE", msg)
    }

    override fun onBind(intent: Intent): IBinder? {
        log("Some component want to bind with the service")
        // We don't provide binding, so return null
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        log("onStartCommand executed with startId: $startId")
        if (intent != null) {
            val action = intent.action
            log("using an intent with action $action")
            when (action) {
                Actions.START.name -> startService()
                Actions.STOP.name -> stopService()
                else -> log("This should never happen. No action in the received intent")
            }
        } else {
            log(
                "with a null intent. It has been probably restarted by the system."
            )

            stopService()
            startService()
        }
        // by returning this we make sure the service is restarted if the system kills the service
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        log("The service has been created".toUpperCase())
        val notification = createNotification()
        startForeground(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        log("The service has been destroyed".toUpperCase())
        Toast.makeText(this, "Service destroyed", Toast.LENGTH_SHORT).show()
    }

    override fun onTaskRemoved(rootIntent: Intent) {
        val restartServiceIntent = Intent(applicationContext, EndlessService::class.java).also {
            it.setPackage(packageName)
        };
        val restartServicePendingIntent: PendingIntent =
            PendingIntent.getService(this, 1, restartServiceIntent, PendingIntent.FLAG_IMMUTABLE);
        applicationContext.getSystemService(Context.ALARM_SERVICE);
        val alarmService: AlarmManager =
            applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager;
        alarmService.set(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + 1000,
            restartServicePendingIntent
        );
    }

    @SuppressLint("MissingPermission")
    private fun startService() {
        if (isServiceStarted) return
        log("Starting the foreground service task")
        Toast.makeText(this, "Service starting its task", Toast.LENGTH_SHORT).show()
        isServiceStarted = true
        setServiceState(this, ServiceState.STARTED)

        // we need this lock so our service gets not affected by Doze Mode
        wakeLock =
            (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EndlessService::lock").apply {
                    acquire()
                }
            }

        // we're starting a loop in a coroutine
        GlobalScope.launch(Dispatchers.IO) {
            log("Starting Scan")
            device = btleService.getBtDevice(bluetoothAdapter, 500000, "00001523-1212-EFDE-1523-785FEABCD123")
            log("Done scanning, device found")
            gatt = btleService.getBtGatt(device!!, true)
            gatt!!.requestConnectionPriority(CONNECTION_PRIORITY_LOW_POWER)
            var charService =
                gatt!!.getService(UUID.fromString("00001523-1212-EFDE-1523-785FEABCD123"))

            var characteristic =
                charService.getCharacteristic(UUID.fromString("00001525-1212-EFDE-1523-785FEABCD123"))

            while (isServiceStarted) {
                log("Starting to write data")
                characteristic.value = getDataPackage()
                gatt!!.writeCharacteristic(characteristic)
                log("Data successfully written")
                delay(60000)
            }

            gatt!!.disconnect()
            gatt!!.close()
            log("End of the loop for the service")
        }
    }

    private fun getDataPackage(): ByteArray {
        val unixEpochSecond = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
        val byte1 = unixEpochSecond.toByte()
        val byte2 = (unixEpochSecond shr (8*1)).toByte()
        val byte3 = (unixEpochSecond shr (8*2)).toByte()
        val byte4 = (unixEpochSecond shr (8*3)).toByte()
        return byteArrayOf(byte1, byte2, byte3, byte4)
    }

    @SuppressLint("MissingPermission")
    private fun stopService() {
        log("Stopping the foreground service")
        Toast.makeText(this, "Service stopping", Toast.LENGTH_SHORT).show()
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }

            stopForeground(true)
            stopSelf()
            bluetoothAdapter.bluetoothLeScanner.stopScan(object : ScanCallback() {})
            gatt?.disconnect()
            gatt?.close()
        } catch (e: Exception) {
            log("Service stopped without being started: ${e.message}")
        }
        isServiceStarted = false
        setServiceState(this, ServiceState.STOPPED)
    }

    private fun createNotification(): Notification {
        val notificationChannelId = "ENDLESS SERVICE CHANNEL"

        // depending on the Android API that we're dealing with we will have
        // to use a specific method to create the notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                notificationChannelId,
                "Endless Service notifications channel",
                NotificationManager.IMPORTANCE_HIGH
            ).let {
                it.description = "Endless Service channel"
                it.enableLights(true)
                it.lightColor = Color.RED
                it.enableVibration(true)
                it.vibrationPattern = longArrayOf(100, 200, 300, 400, 500, 400, 300, 200, 400)
                it
            }
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent: PendingIntent =
            Intent(this, MainActivity2::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
            }

        val builder: Notification.Builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(
                this,
                notificationChannelId
            ) else Notification.Builder(this)

        return builder
            .setContentTitle("Endless Service")
            .setContentText("This is your favorite endless service working")
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setTicker("Ticker text")
            .setPriority(Notification.PRIORITY_HIGH) // for under android 26 compatibility
            .build()
    }
}

enum class Actions {
    START,
    STOP
}