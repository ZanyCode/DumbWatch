package org.zanytek.zanytime

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat.getSystemService
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.Continuation
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@SuppressLint("MissingPermission")
class BtleService(val context: Context) {
    public var onReconnect: (() -> Unit)? = null

    suspend fun test()
    {
        return suspendCoroutine { cont ->
            cont.resumeWithException(Throwable("FUCK"))
        }
    }

    suspend inline fun <T> suspendCoroutineWithTimeout(timeout: Long, crossinline block: (Continuation<T>) -> Unit ) : T? {
        var finalValue : T? = null
        withTimeoutOrNull(timeout) {
            finalValue = suspendCancellableCoroutine(block = block)
        }
        return finalValue
    }

    suspend fun getBtDevice(adapter: BluetoothAdapter, timeoutMillis: Long, serviceUUID: String): BluetoothDevice {
        val scanner = adapter.bluetoothLeScanner
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(serviceUUID)).build()
        val filters = listOf<ScanFilter>(
            filter
        )

        val device = suspendCoroutineWithTimeout<BluetoothDevice>(timeoutMillis) { cont ->
            var done = false
            scanner.startScan(filters, ScanSettings.Builder().build(), object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    if(!done) {
                        super.onScanResult(callbackType, result)
                        done = true
                        Log.d("MAIN", "*********************GOT DEVICE ${result.device.address}")
                        scanner.stopScan(object : ScanCallback() { })
                        cont.resumeWith(Result.success(result.device))
                    }
                }
            })
        }

        if(device == null) {
            scanner.stopScan(object : ScanCallback() { override fun onScanResult(callbackType: Int, result: ScanResult) {} })
            throw Throwable("Scan timed out")
        }
        else
            return device
    }

    suspend fun getBtGatt(device: BluetoothDevice, autoConnect: Boolean) : BluetoothGatt {
        return suspendCoroutine { cont ->
            var resumed = false

            device.connectGatt(context, autoConnect, object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    val deviceAddress = gatt.device.address

                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                            Log.w("BluetoothGattCallback", "Successfully connected to $deviceAddress")
                            Handler(Looper.getMainLooper()).post {
                                if(!gatt.discoverServices() && !resumed) {
                                    resumed = true
                                    cont.resumeWith(Result.failure(Throwable("Service Discovery Failed")))
                                }
                            }

                            if(onReconnect != null)
                            {
                                onReconnect?.invoke()
                            }
                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                            Log.w("BluetoothGattCallback", "Successfully disconnected from $deviceAddress")
//                            gatt.close()
                            if(!resumed) {
                                resumed = true
                                cont.resumeWith(Result.failure(Throwable("Somehow got into disconnected state")))
                            }
                        }
                    } else {
                        Log.w("BluetoothGattCallback", "Error $status encountered for $deviceAddress! Disconnecting...")
                        gatt.close()
                        if(!resumed) {
                            resumed = true
                            cont.resumeWith(Result.failure(Throwable("Error $status encountered for $deviceAddress! Disconnecting...")))
                        }
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                    super.onServicesDiscovered(gatt, status)
                    if(gatt != null && !resumed)
                    {
                        resumed = true
                        cont.resumeWith(Result.success(gatt))
                    }
                    else if(!resumed)
                    {
                        resumed = true
                        cont.resumeWith(Result.failure(Throwable("Lost the Bluetooth Gatt")))
                    }

                }
            })
        }
    }
}