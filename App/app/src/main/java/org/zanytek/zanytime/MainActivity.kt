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

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertisingSet
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelUuid
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity


class MainActivity : AppCompatActivity() {

    private var gattServiceConn: GattServiceConn? = null
    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val gattCharacteristicValue = findViewById<EditText>(R.id.editTextGattCharacteristicValue)
        gattCharacteristicValue.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                gattServiceConn?.binding?.setMyCharacteristicValue(p0.toString())
            }

            override fun afterTextChanged(p0: Editable?) {}
        })
        advertiseData()
        // Startup our Bluetooth GATT service explicitly so it continues to run even if
        // this activity is not in focus
//        startForegroundService(Intent(this, GattService::class.java))
    }

    @SuppressLint("MissingPermission")
    private fun advertiseData() {
        val advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if(!bluetoothAdapter.isLe2MPhySupported) {
            Log.e("MainAct", "2M PHY is not supported")
        }

        if(!bluetoothAdapter.isLeExtendedAdvertisingSupported) {
            Log.e("MainAct", "BT LE extended advertising is not supported")
        }

        val maxDataLength = bluetoothAdapter.leMaximumAdvertisingDataLength

//        val parameters = AdvertisingSetParameters.Builder()
//            .setLegacyMode(false)
//            .setInterval(AdvertisingSetParameters.INTERVAL_MIN)
//            .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_MAX)
//            .setPrimaryPhy(BluetoothDevice.PHY_LE_1M)
//            .setSecondaryPhy(BluetoothDevice.PHY_LE_2M)

        val parameters = AdvertisingSetParameters.Builder()
            .setLegacyMode(true)
            .setConnectable(true)
            .setScannable(true)
            .setInterval(AdvertisingSetParameters.INTERVAL_MIN)
            .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_MAX)

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false) // Including it will blow the length
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(ParcelUuid(GattService.MyServiceProfile.MY_SERVICE_UUID))
//            .addServiceData(
//                ParcelUuid(GattService.MyServiceProfile.MY_SERVICE_UUID),
//                "t".toByteArray()
//            )
            .build()

        val callback = object: AdvertisingSetCallback() {
            override fun onAdvertisingSetStarted(
                advertisingSet: AdvertisingSet?,
                txPower: Int,
                status: Int
            ) {
                Log.i(
                    "", "onAdvertisingSetStarted(): txPower:" + txPower + " , status: "
                            + status
                )
            }

            override fun onAdvertisingSetStopped(advertisingSet: AdvertisingSet?) {
                Log.i("", "onAdvertisingSetStopped():")
            }
        }

        advertiser.startAdvertisingSet(parameters.build(), data, null, null, null, callback)
    }

    override fun onStart() {
        super.onStart()

//        val latestGattServiceConn = GattServiceConn()
//        if (bindService(Intent(this, GattService::class.java), latestGattServiceConn, 0)) {
//            gattServiceConn = latestGattServiceConn
//        }
    }



    override fun onStop() {
        super.onStop()

        if (gattServiceConn != null) {
            unbindService(gattServiceConn!!)
            gattServiceConn = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // We only want the service around for as long as our app is being run on the device
        stopService(Intent(this, GattService::class.java))
    }

    private class GattServiceConn : ServiceConnection {
        var binding: DeviceAPI? = null

        override fun onServiceDisconnected(name: ComponentName?) {
            binding = null
        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            binding = service as? DeviceAPI
        }
    }
}