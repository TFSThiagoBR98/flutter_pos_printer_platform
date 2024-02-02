package com.sersoluciones.flutter_pos_printer_platform.bluetooth

import android.bluetooth.*
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import com.sersoluciones.flutter_pos_printer_platform.bluetooth.SampleGattAttributes.Companion.CLIENT_CHARACTERISTIC_CONFIG
import com.sersoluciones.flutter_pos_printer_platform.bluetooth.SampleGattAttributes.Companion.HEART_RATE_MEASUREMENT
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

private const val TAG = "BluetoothBleConnection"

class BluetoothBleConnection(
    private val mContext: Context,
    private val mHandler: Handler,
    private var autoConnect: Boolean = false
) : IBluetoothConnection {

    private var bluetoothGatt: BluetoothGatt? = null
    private var mCharacteristic: BluetoothGattCharacteristic? = null
    private var mState: Int = BluetoothConstants.STATE_NONE

    /**
     * Return the current connection state.
     * Set the current state of the chat connection
     */
    @get:Synchronized
    @set:Synchronized
    override var state: Int
        get() = mState
        set(state) {
            if (state != BluetoothConstants.STATE_FAILED && state != BluetoothConstants.STATE_CONNECTED)
                // Give the new state to the Handler so the UI Activity can update
                mHandler.obtainMessage(BluetoothConstants.MESSAGE_STATE_CHANGE, state, -1).sendToTarget()
            if (state == BluetoothConstants.STATE_FAILED) mState = BluetoothConstants.STATE_NONE
            mState = state
        }


    /**
     * connect to bluetooth device
     */
    override fun connect(address: String, result: MethodChannel.Result) {
        if (!address.matches(Regex(BluetoothConstants.BLUETOOTH_REGEX))) return
        if (mState == BluetoothConstants.STATE_CONNECTED) return
        state = BluetoothConstants.STATE_CONNECTING

        BluetoothAdapter.getDefaultAdapter()?.let { adapter ->
            try {
                val device = adapter.getRemoteDevice(address)
                val bluetoothGattCallback = ResponseBluetoothGattCallback(result)

                // connect to the GATT server on the device
                bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    device.connectGatt(
                        mContext,
                        autoConnect,
                        bluetoothGattCallback,
                        BluetoothDevice.TRANSPORT_LE
                    )
                } else {
                    device.connectGatt(mContext, autoConnect, bluetoothGattCallback)
                }

                // Send the name of the connected device back to the UI Activity
                val msg = mHandler.obtainMessage(BluetoothConstants.MESSAGE_DEVICE_NAME)
                val bundle = Bundle()
                bundle.putString(BluetoothConstants.DEVICE_NAME, device.name)
                msg.data = bundle
                mHandler.sendMessage(msg)

            } catch (exception: IllegalArgumentException) {
                state = BluetoothConstants.STATE_FAILED
                Log.w(TAG, "Device not found with provided address.")
                // Give the new state to the Handler so the UI Activity can update
                mHandler.obtainMessage(BluetoothConstants.MESSAGE_STATE_CHANGE, state, -1, result).sendToTarget()
                state = BluetoothConstants.STATE_NONE
            }
            // connect to the GATT server on the device
        } ?: run {
            Log.w(TAG, "BluetoothAdapter not initialized")
            return
        }

    }

    /**
     * finish connection
     */
    override fun stop() {
        bluetoothGatt?.let { gatt ->
            gatt.disconnect()
            gatt.close()
            bluetoothGatt = null
            state = BluetoothConstants.STATE_NONE
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun write(out: ByteArray?) {
        GlobalScope.launch {
            Log.d(TAG, "Max Packet Size: ${out?.size}")
            Log.d(TAG, "Connected to device")
            val chunkSize = 500
            if (out!!.size > chunkSize) {
                var chunks: Int = out.size / chunkSize
                if (out.size % chunkSize > 0) {
                    ++chunks
                }
                for (i in 0 until chunks) {
                    val buffer: ByteArray = Arrays.copyOfRange(out, i * chunkSize, chunkSize + i * chunkSize)
                    Log.d(TAG, "Print chunk $i size ${buffer.size}")
                    mCharacteristic?.let { characteristic ->
                        bluetoothGatt?.let { gatt ->
                            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                            characteristic.value = buffer
                            gatt.writeCharacteristic(characteristic)
                            delay(1500)
                        } ?: error("Not connected to a BLE device!")
                    }
                }
            } else {
                mCharacteristic?.let { characteristic ->
                    bluetoothGatt?.let { gatt ->
                        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        characteristic.value = out
                        gatt.writeCharacteristic(characteristic)
                        // Share the sent message back to the UI Activity
                        mHandler.obtainMessage(BluetoothConstants.MESSAGE_WRITE, -1, -1, out)
                            .sendToTarget()
                    } ?: error("Not connected to a BLE device!")
                }
            }
        }
    }

    /***
     *
     */
    private inner class ResponseBluetoothGattCallback(private val result: MethodChannel.Result) : BluetoothGattCallback() {
        private var mmChannelResult: MethodChannel.Result? = null

        init {
            mmChannelResult = result
        }

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, " ---------- onConnectionStateChange: newState $newState status $status")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    // successfully connected to the GATT Server
                    Log.d(TAG, "onConnectionStateChange: STATE_CONNECTED")
                    state = BluetoothConstants.STATE_CONNECTED

                    if (mmChannelResult != null) {
                        // Give the new state to the Handler so the UI Activity can update
                        mHandler.obtainMessage(BluetoothConstants.MESSAGE_STATE_CHANGE, state, -1, result).sendToTarget()
                        mmChannelResult = null
                    } else {
                        mHandler.obtainMessage(BluetoothConstants.MESSAGE_STATE_CHANGE, state, -1).sendToTarget()
                    }
                    // Attempts to discover services after successful connection.
                    bluetoothGatt?.discoverServices()

                }
                BluetoothProfile.STATE_CONNECTING -> {
                    Log.d(TAG, "onConnectionStateChange: STATE_CONNECTING")
                    // connecting from the GATT Server
                    state = BluetoothConstants.STATE_CONNECTING
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "onConnectionStateChange: STATE_DISCONNECTED")
                    if (mmChannelResult != null) {
                        // disconnected from the GATT Server
                        state = BluetoothConstants.STATE_FAILED
                        // Give the new state to the Handler so the UI Activity can update
                        mHandler.obtainMessage(BluetoothConstants.MESSAGE_STATE_CHANGE, state, -1, result).sendToTarget()
                        mmChannelResult = null
                    }

                    state = BluetoothConstants.STATE_NONE
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {

                displayGattServices(getSupportedGattServices())
            } else {
                Log.w(TAG, "onServicesDiscovered received: $status")
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(characteristic)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            broadcastUpdate(characteristic)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            with(characteristic) {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        Log.i(
                            "BluetoothGattCallback",
                            "Wrote to characteristic $uuid | value: $value"
                        )
                    }
                    BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH -> {
                        Log.e("BluetoothGattCallback", "Write exceeded connection ATT MTU!")
                    }
                    BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> {
                        Log.e("BluetoothGattCallback", "Write not permitted for $uuid!")
                    }
                    else -> {
                        Log.e(
                            "BluetoothGattCallback",
                            "Characteristic write failed for $uuid, error: $status"
                        )
                    }
                }
            }
        }
    }


    /***
     * Demonstrates how to iterate through the supported GATT
     * Services/Characteristics.
     * In this sample, we populate the data structure that is bound to the
     * ExpandableListView on the UI.
     */
    private fun displayGattServices(gattServices: List<BluetoothGattService>?) {
        if (gattServices == null) return
        var uuid: String?

        // Loops through available GATT Services.
        gattServices.forEach { gattService ->
            uuid = gattService.uuid.toString()
            Log.d(TAG, " ------- service -> uuid: $uuid")
            if (gattService.uuid == UUID.fromString("000018f0-0000-1000-8000-00805f9b34fb")) {
                // Loops through available Characteristics.
                gattService.characteristics.forEach { gattCharacteristic ->

                    // Log.d(TAG, " ------- gattCharacteristics -> uuid: ${gattCharacteristic.uuid} value ${gattCharacteristic.value}")
                    if (UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG) == gattCharacteristic.uuid) {
//                        gatt.setCharacteristicNotification(characteristic, true)
//
//                        val descriptor: BluetoothGattDescriptor =
//                            characteristic.getDescriptor(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG))
//                                ?: return

                        Log.d(TAG, " *************** BluetoothGatt descriptor ${gattCharacteristic.uuid}")
                        mCharacteristic = gattCharacteristic

//                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
//                        gatt.writeDescriptor(descriptor)
                    }
                }
            }
        }

    }

    fun getSupportedGattServices(): List<BluetoothGattService>? {
        return bluetoothGatt?.services
    }


    // Read from the InputStream
    private var buffer = ArrayList<Byte>()

    private fun broadcastUpdate(characteristic: BluetoothGattCharacteristic?) {

        if (characteristic != null) {
            when (characteristic.uuid) {
                UUID_HEART_RATE_MEASUREMENT -> {

                }
                else -> {
                    // For all other profiles, writes the data formatted in HEX.
                    val data: ByteArray? = characteristic.value
                    if (data?.isNotEmpty() == true) {

                        // 30 33 20 30 30 20 30 30 20 30 30 20 30 30 20 30 30 20 44 38 20 38 32 20 0D 0A
                        // 48 51 32 48 48 32 48 48 32 48 48 32 48 48 32 48 48 32 68 56 32 56 50 32 13 10
                        for (byte in data) {

                            buffer.add(byte)
//                            buffer += byte
                            if (byte.toInt() == 13) {
                                sendMsg()
                                break
                            }
                        }

                    }
                }
            }
        }
    }

    private fun sendMsg() {
        val hexString: String = buffer.joinToString(separator = " ") {
            String.format("%02X", it)
        }
//        Log.d(TAG, "sendMsg data $hexString value size ${hexString.length}")
        // Send the obtained bytes to the UI Activity
        mHandler.obtainMessage(
            BluetoothConstants.MESSAGE_READ,
            buffer.size,
            -1,
            buffer.toByteArray()
        ).sendToTarget()

        buffer = arrayListOf()

    }

    companion object {

        val UUID_HEART_RATE_MEASUREMENT: UUID =
            UUID.fromString(HEART_RATE_MEASUREMENT)

    }
}