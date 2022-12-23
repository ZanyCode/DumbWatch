package org.zanytek.zanytime
/*
 * Copyright (c) 2020, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */


import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.*


/**
 * Connects with a Bluetooth LE GATT service and takes care of its notifications. The service
 * runs as a foreground service, which is generally required so that it can run even
 * while the containing app has no UI. It is also possible to have the service
 * started up as part of the OS boot sequence using code similar to the following:
 *
 * <pre>
 *     class OsNotificationReceiver : BroadcastReceiver() {
 *          override fun onReceive(context: Context?, intent: Intent?) {
 *              when (intent?.action) {
 *                  // Start our Gatt service as a result of the system booting up
 *                  Intent.ACTION_BOOT_COMPLETED -> {
 *                     context?.startForegroundService(Intent(context, GattService::class.java))
 *                  }
 *              }
 *          }
 *      }
 * </pre>
 */
class GattService : Service() {
    // Debug constants
    private var serviceUUID = "2b12b859-1407-41b4-977b-9174e0914301"
    private var charUUID = "e182417c-a449-47ce-bf93-0d9c07e68f02"
    private var deviceName = "DumbWatchDBG"

    // Release constants
//    private var serviceUUID = "d9d919ee-b681-4a63-9b2d-9fb22fc56b3b"
//    private var charUUID = "f681631f-f2d3-42e2-b769-ab1ef3011029"
//    private var deviceName = "DumbWatch"

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }
    var device: BluetoothDevice? = null
    var gatt: BluetoothGatt? = null
    var characteristic: BluetoothGattCharacteristic? = null
    var btleService: BtleService = BtleService(this)
    private lateinit var curNotification: NotificationCompat.Builder
    private val NOTIF_ID = 1


    override fun onCreate() {
        super.onCreate()

        // Setup as a foreground service
        val notificationChannel = NotificationChannel(
            GattService::class.java.simpleName,
            resources.getString(R.string.gatt_service_name),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val notificationService =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationService.createNotificationChannel(notificationChannel)

        curNotification = NotificationCompat.Builder(this, GattService::class.java.simpleName)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(resources.getString(R.string.gatt_service_name))
            .setContentText("NoUpdate Yet")
            .setAutoCancel(true)

        startForeground(NOTIF_ID, curNotification.build())
    }

    /**
     * This is the method that can be called to update the Notification
     */
    private fun updateNotification(text: String) {
        val notificationService =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = curNotification.setContentText(text)
        notificationService.notify(NOTIF_ID, notification.build())
    }

    fun disconnectWatchInternal() : Boolean {
        gatt?.disconnect()
        gatt?.close()
        device = null
        gatt = null
        characteristic = null
        log("Disconnected from Watch")
        return true
    }

    fun writeDataToWatchInternal(data: ByteArray): Boolean {
        characteristic?.value = data
        gatt!!.writeCharacteristic(characteristic)
        log("Done writing data")

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        val currentDate = sdf.format(Date())
        updateNotification("Updated at ${currentDate}")
        return true
    }

    override fun onDestroy() {
        disconnectWatchInternal()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? =
        when (intent?.action) {
            DATA_PLANE_ACTION -> {
                DataPlane()
            }
            else -> null
        }

    override fun onUnbind(intent: Intent?): Boolean =
        when (intent?.action) {
            DATA_PLANE_ACTION -> {
                true
            }
            else -> false
        }

    fun log(msg: String) {
        Log.d("ENDLESS-SERVICE", msg)
    }

    /**
     * A binding to be used to interact with data of the service
     */
    inner class DataPlane : Binder() {
        suspend fun connectToWatch(): Boolean {
            device = btleService.getBtDevice(
                bluetoothAdapter,
                500000,
                serviceUUID
            )
            log("Done scanning, device found")
            gatt = btleService.getBtGatt(device!!, true)
            gatt!!.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER)
            val charService =
                gatt!!.getService(UUID.fromString(serviceUUID))

            characteristic = charService.getCharacteristic(UUID.fromString(charUUID))
            return true
        }

        fun writeDataToWatch(data: ByteArray): Boolean {
            return writeDataToWatchInternal(data)
        }

        fun disconnectWatch(): Boolean {
            return disconnectWatchInternal()
        }
    }

    /*
     * Manages the entire GATT service, declaring the services and characteristics on offer
     */
    companion object {
        /**
         * A binding action to return a binding that can be used in relation to the service's data
         */
        const val DATA_PLANE_ACTION = "data-plane"

        private const val TAG = "gatt-service"
    }
}