package org.zanytek.zanytime

import android.bluetooth.le.*
import android.os.ParcelUuid
import android.util.Log
import android.widget.Toast


object BleAdvertiser {
    private const val TAG = "ble-advertiser"

    class Callback : AdvertisingSetCallback() {
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

    fun settings(): AdvertisingSetParameters {
        val parameters = AdvertisingSetParameters.Builder()
            .setLegacyMode(false)
            .setInterval(AdvertisingSetParameters.INTERVAL_MIN)
            .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_MAX)

        return parameters.build()
//        return AdvertiseSettings.Builder()
//            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
//            .setConnectable(true)
//            .setTimeout(0)
//            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
//            .build()
    }

    fun advertiseData(): AdvertiseData {
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false) // Including it will blow the length
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(ParcelUuid(GattService.MyServiceProfile.MY_SERVICE_UUID))
            .addServiceData(
                ParcelUuid(GattService.MyServiceProfile.MY_SERVICE_UUID),
                "test".toByteArray()
            )
            .build()

        return data
    }
}