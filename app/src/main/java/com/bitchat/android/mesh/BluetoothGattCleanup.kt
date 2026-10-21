package com.bitchat.android.mesh

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattServer
import android.util.Log

// Cleanup must remain safe after Android revokes Bluetooth access or shuts down the adapter.
internal fun BluetoothGatt.disconnectSafely() {
    try { disconnect() }
    catch (e: SecurityException) { Log.w("BluetoothGattCleanup", "Bluetooth access revoked during disconnect", e) }
    catch (e: RuntimeException) { Log.w("BluetoothGattCleanup", "GATT disconnect failed", e) }
}

internal fun BluetoothGatt.closeSafely() {
    try { close() }
    catch (e: SecurityException) { Log.w("BluetoothGattCleanup", "Bluetooth access revoked during close", e) }
    catch (e: RuntimeException) { Log.w("BluetoothGattCleanup", "GATT close failed", e) }
}

internal fun BluetoothGattServer.closeSafely() {
    try { close() }
    catch (e: SecurityException) { Log.w("BluetoothGattCleanup", "Bluetooth access revoked during server close", e) }
    catch (e: RuntimeException) { Log.w("BluetoothGattCleanup", "GATT server close failed", e) }
}
