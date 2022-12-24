package org.zanytek.zanytime

import android.app.Service
import android.bluetooth.*
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.*
import kotlin.coroutines.suspendCoroutine

class SendDataWorker(val context: Context, userParameters: WorkerParameters) :
    CoroutineWorker(context, userParameters) {

    // Release constants
    private var serviceUUID = "d9d919ee-b681-4a63-9b2d-9fb22fc56b3b"
    private var charUUID = "f681631f-f2d3-42e2-b769-ab1ef3011029"
    private var deviceName = "DumbWatch"

    // Debug constants
//    private var serviceUUID = "2b12b859-1407-41b4-977b-9174e0914301"
//    private var charUUID = "e182417c-a449-47ce-bf93-0d9c07e68f02"
//    private var deviceName = "DumbWatchDBG"

    override suspend fun doWork(): Result {
        Log.i("SendDataWorker", "Entering doWork")
        sendDataToWatch()
        writeLatestUpdateToPrefs()
        Log.i("SendDataWorker", "Finished with DoWork")
        return Result.success()
    }

    private suspend fun sendDataToWatch() {
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

        val device = bluetoothManager.adapter.bondedDevices.first { it.name == deviceName }
        val gatt = getBtGatt(device!!, false)
        gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER)
        val charService =
            gatt.getService(UUID.fromString(serviceUUID))
        val characteristic = charService.getCharacteristic(UUID.fromString(charUUID))
        characteristic?.value = getDataPackage()
        gatt.writeCharacteristic(characteristic)
        gatt.disconnect()
        gatt.close()
    }

    private fun writeLatestUpdateToPrefs() {
        val prefs = context.getSharedPreferences("LastUpdate", Service.MODE_PRIVATE)
        val edit = prefs.edit()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        val currentDate = sdf.format(Date())
        edit.putString("UpdateTime", currentDate)
        edit.apply()
    }

    private suspend fun getDataPackage(): ByteArray {
        val unixEpochSecond = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
        val stepsToday = readStepsForCurrentDay()
        Log.i("MainActivity", "Steps: $stepsToday")
        val byte1 = unixEpochSecond.toByte()
        val byte2 = (unixEpochSecond shr (8 * 1)).toByte()
        val byte3 = (unixEpochSecond shr (8 * 2)).toByte()
        val byte4 = (unixEpochSecond shr (8 * 3)).toByte()
        val byte5 = stepsToday.toByte()
        val byte6 = (stepsToday shr (8 * 1)).toByte()
        val byte7 = (stepsToday shr (8 * 2)).toByte()
        return byteArrayOf(byte1, byte2, byte3, byte4, byte5, byte6, byte7)
    }

    suspend fun readStepsForCurrentDay(): Long {
        val healthConnectClient = HealthConnectClient.getOrCreate(context)
        val startTime = LocalDate.now().atTime(LocalTime.MIN)
        val endTime = LocalDate.now().atTime(LocalTime.MAX)
        val response =
            healthConnectClient.readRecords(
                ReadRecordsRequest(
                    StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )

        return response.records.sumOf { it.count }
    }

    suspend fun getBtGatt(device: BluetoothDevice, autoConnect: Boolean): BluetoothGatt {
        return suspendCoroutine { cont ->
            var resumed = false

            device.connectGatt(context, autoConnect, object : BluetoothGattCallback() {
                override fun onConnectionStateChange(
                    gatt: BluetoothGatt,
                    status: Int,
                    newState: Int
                ) {
                    val deviceAddress = gatt.device.address

                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                            Log.w(
                                "BluetoothGattCallback",
                                "Successfully connected to $deviceAddress"
                            )
                            Handler(Looper.getMainLooper()).post {
                                if (!gatt.discoverServices() && !resumed) {
                                    resumed = true
                                    cont.resumeWith(kotlin.Result.failure(Throwable("Service Discovery Failed")))
                                }
                            }

                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                            Log.w(
                                "BluetoothGattCallback",
                                "Successfully disconnected from $deviceAddress"
                            )
//                            gatt.close()
                            if (!resumed) {
                                resumed = true
                                cont.resumeWith(kotlin.Result.failure(Throwable("Somehow got into disconnected state")))
                            }
                        }
                    } else {
                        Log.w(
                            "BluetoothGattCallback",
                            "Error $status encountered for $deviceAddress! Disconnecting..."
                        )
                        gatt.close()
                        if (!resumed) {
                            resumed = true
                            cont.resumeWith(kotlin.Result.failure(Throwable("Error $status encountered for $deviceAddress! Disconnecting...")))
                        }
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                    super.onServicesDiscovered(gatt, status)
                    if (gatt != null && !resumed) {
                        resumed = true
                        cont.resumeWith(kotlin.Result.success(gatt))
                    } else if (!resumed) {
                        resumed = true
                        cont.resumeWith(kotlin.Result.failure(Throwable("Lost the Bluetooth Gatt")))
                    }

                }
            })
        }
    }
}