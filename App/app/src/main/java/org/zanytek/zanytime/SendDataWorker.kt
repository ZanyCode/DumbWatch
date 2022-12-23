package org.zanytek.zanytime

import android.app.Service
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*
import kotlin.coroutines.suspendCoroutine

class SendDataWorker(val context: Context, userParameters: WorkerParameters) :
    CoroutineWorker(context, userParameters) {

    // Release constants
//    private var serviceUUID = "d9d919ee-b681-4a63-9b2d-9fb22fc56b3b"
//    private var charUUID = "f681631f-f2d3-42e2-b769-ab1ef3011029"
//    private var deviceName = "DumbWatch"

    // Debug constants
    private var serviceUUID = "2b12b859-1407-41b4-977b-9174e0914301"
    private var charUUID = "e182417c-a449-47ce-bf93-0d9c07e68f02"
    private var deviceName = "DumbWatchDBG"

    private fun getDataPackage(): ByteArray {
        val unixEpochSecond = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
        val stepsToday = 12000
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

    override suspend fun doWork(): Result {
        Log.i("SendDataWorker", "Entering doWork")
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

        val device = bluetoothManager.adapter.bondedDevices.first { it.name == deviceName }
        val gatt = BtleService(this.context).getBtGatt(device!!, false)
        gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER)
        val charService =
            gatt.getService(UUID.fromString(serviceUUID))
        val characteristic = charService.getCharacteristic(UUID.fromString(charUUID))
        characteristic?.value = getDataPackage()
        gatt.writeCharacteristic(characteristic)
        gatt.disconnect()
        gatt.close()

        val prefs = context.getSharedPreferences("LastUpdate", Service.MODE_PRIVATE)
        val currentSteps = prefs.getString("UpdateTime", "None")
        val edit = prefs.edit()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        val currentDate = sdf.format(Date())
        edit.putString("UpdateTime", currentDate)
        edit.apply()

        Log.i("SendDataWorker", "Finished with DoWork")
        return Result.success()
    }
}