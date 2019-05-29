package net.chrivieh.brewce;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

import layout.TemperatureChartFragment;

public class ActuatorNode {

    private Context mContext;

    public static final String TAG = ActuatorNode.class.getSimpleName();

    public final static String ACTION_GATT_CONNECTED =
            "net.chrivieh.brewce.ActuatorNode.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "net.chrivieh.brewce.ActuatorNode.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_DATA_AVAILABLE =
            "net.chrivieh.brewce.ActuatorNode.ACTION_DATA_AVAILABLE";
    public final static String ACTION_DATA_WRITE =
            "net.chrivieh.brewce.ActuatorNode.ACTION_DATA_WRITE";
    public final static String EXTRA_DATA =
            "net.chrivieh.brewce.ActuatorNode..EXTRA_DATA";
    public final static String EXTRA_DATA_FLOAT =
            "net.chrivieh.brewce.ActuatorNode.EXTRA_DATA_FLOAT";
    public final static String EXTRA_DATA_DEVICE_ADDRESS =
            "net.chrivieh.brewce.ActuatorNode.EXTRA_DEVICE_ADDRESS";

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;
    private static final int STATE_DISCONNECTING = 0;

    private BluetoothDevice mBluetoothDevice;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattCharacteristic mWriteCharacteristics;

    private int mConnectionState = STATE_DISCONNECTED;

    public ActuatorNode (Context context, BluetoothDevice device) {
        this.mContext = context;
        this.mBluetoothDevice = device;
    }

    public void connect() {
        if(mConnectionState != STATE_DISCONNECTED)
            return;
        Log.i(TAG, "Trying to connect to " + mBluetoothDevice.getAddress());
        mBluetoothGatt = mBluetoothDevice.connectGatt(null, true, mGattCallback);
        mConnectionState = STATE_CONNECTING;
    }

    public void disconnect() {
        mConnectionState = STATE_DISCONNECTING;
        mBluetoothGatt.disconnect();
    }

    public boolean isConnected() {
        return mConnectionState == STATE_CONNECTED;
    }

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);

            String intentAction;
            if(newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected to GATT server.");
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction, gatt.getDevice().getAddress());
                mBluetoothGatt.discoverServices();
            }
            else if(newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server.");
                intentAction = ACTION_GATT_DISCONNECTED;
                if(mConnectionState == STATE_CONNECTED) {
                    Log.i(TAG, "Trying to reconnect.");
                    connect();
                }
                mConnectionState = STATE_DISCONNECTED;
                broadcastUpdate(intentAction, gatt.getDevice().getAddress());
            } else {
                Log.i(TAG, "Other error.");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if(status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service =
                        mBluetoothGatt.getService(UUID.fromString("0000fe84-0000-1000-8000-00805f9b34fb"));
                mWriteCharacteristics =
                        service.getCharacteristic(UUID.fromString("2d30c083-f39f-4ce6-923f-3484ea480596"));
                BluetoothGattCharacteristic readChar =
                        service.getCharacteristic(UUID.fromString("2d30c082-f39f-4ce6-923f-3484ea480596"));
                BluetoothGattDescriptor desc = readChar.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                mBluetoothGatt.setCharacteristicNotification(readChar, true);
                desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                mBluetoothGatt.writeDescriptor(desc);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            if(status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic, gatt.getDevice().getAddress());
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic, gatt.getDevice().getAddress());
        }
    };

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        mContext.sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final String deviceAddress)
    {
        final Intent intent = new Intent(action);
        intent.putExtra(EXTRA_DATA_DEVICE_ADDRESS, deviceAddress);
        mContext.sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic,
                                 final String deviceAddress) {
        final Intent intent = new Intent(action);

        final byte[] data = characteristic.getValue();
        if(data != null && data.length > 0) {
            final StringBuilder stringBuilder = new StringBuilder();
            for(byte b: data) {
                stringBuilder.append(String.format("%02X ", b));
            }
            float temp = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getFloat();
            intent.putExtra(EXTRA_DATA, data);
            intent.putExtra(EXTRA_DATA_FLOAT, temp);
            intent.putExtra(EXTRA_DATA_DEVICE_ADDRESS, deviceAddress);
            TemperatureChartFragment.TemperatureMeasurement temperatureMeasurement
                    = new TemperatureChartFragment.TemperatureMeasurement(
                    temp, SystemClock.uptimeMillis());
            TemperatureChartFragment.temperatureMeasurements.add(temperatureMeasurement);
        }

        mContext.sendBroadcast(intent);
    }

    private void broadcastWriteUpdate(final String action,
                                      final String deviceAddress,
                                      byte[] data)
    {
        final Intent intent = new Intent(action);
        intent.putExtra(EXTRA_DATA_DEVICE_ADDRESS, deviceAddress);
        if(data[0] != 0)
            intent.putExtra(EXTRA_DATA, (int)(data[1] & 0xFF));
        else
            intent.putExtra(EXTRA_DATA, (int)0);
        mContext.sendBroadcast(intent);
    }

    public void writeUInt16(int data) {
        if(mWriteCharacteristics == null)
            return;
        mWriteCharacteristics.setValue(data, BluetoothGattCharacteristic.FORMAT_UINT16, 0);
        mBluetoothGatt.writeCharacteristic(mWriteCharacteristics);
    }

    public void write(byte[] data) {
        if(mWriteCharacteristics == null)
            return;
        mWriteCharacteristics.setValue(data);
        mBluetoothGatt.writeCharacteristic(mWriteCharacteristics);

        broadcastWriteUpdate(ACTION_DATA_WRITE, mBluetoothDevice.getAddress(), data);
        Log.d(TAG, "write " + (int)(data[0] & 0x00ff) + " " + (int)(data[1] & 0x00ff) + " to actuator");
    }
}
