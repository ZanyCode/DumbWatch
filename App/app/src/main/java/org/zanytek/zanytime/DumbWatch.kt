package org.zanytek.zanytime

import android.bluetooth.*
import android.content.Context
import android.util.Log
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*
import kotlin.random.Random

class DumbWatch(val context: Context) {
    // Release constants
//    private var serviceUUID = "d9d919ee-b681-4a63-9b2d-9fb22fc56b3b"
//    private var charUUID = "f681631f-f2d3-42e2-b769-ab1ef3011029"
//    private var deviceName = "DumbWatch"

    // Debug constants
    private var serviceUUID = "2b12b859-1407-41b4-977b-9174e0914301"
    private var charUUID = "e182417c-a449-47ce-bf93-0d9c07e68f02"
    private var deviceName = "DumbWatchDBG"

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    var device: BluetoothDevice? = null
    var gatt: BluetoothGatt? = null
    var characteristic: BluetoothGattCharacteristic? = null
    var btleService: BtleService = BtleService(this.context)

    suspend fun connectToBondedWatch(): Boolean {
        device = bluetoothAdapter.bondedDevices.first { it.name == deviceName }
        gatt = btleService.getBtGatt(device!!, false)
        gatt!!.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER)
        val charService =
            gatt!!.getService(UUID.fromString(serviceUUID))
        characteristic = charService.getCharacteristic(UUID.fromString(charUUID))
        characteristic?.value = getDataPackage()
        gatt!!.writeCharacteristic(characteristic)
        gatt?.disconnect()
        gatt?.close()
        return true
    }

//    suspend fun connectToWatch(): Boolean {
//        device = btleService.getBtDevice(
//            bluetoothAdapter,
//            500000,
//            serviceUUID
//        )
//        log("Done scanning, device found")
//        gatt = btleService.getBtGatt(device!!, true)
//        gatt!!.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER)
//        val charService =
//            gatt!!.getService(UUID.fromString(serviceUUID))
//
//        characteristic = charService.getCharacteristic(UUID.fromString(charUUID))
//        return true
//    }

    private fun getDataPackage(): ByteArray {
        val unixEpochSecond = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
        val stepsToday = Random.nextInt(1000, 18000)
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

    fun log(msg: String) {
        Log.d("DumbWatch", msg)
    }
}