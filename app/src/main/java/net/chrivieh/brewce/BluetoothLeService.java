package net.chrivieh.brewce;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import com.github.mikephil.charting.data.Entry;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import layout.TemperatureChartFragment;

public class BluetoothLeService extends Service {
    public static final String TAG = BluetoothLeService.class.getSimpleName();

    public final UUID UUID_BEACON_BREWCE =
            UUID.fromString("b5e9d1f2-cdb3-4758-a1b0-1d6ddd22dd0d");

    private final IBinder mBinder = new LocalBinder();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothLeScanner;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattCharacteristic mWriteCharacteristics;

    private Handler mHandler;

    private int mConnectionState = STATE_DISCONNECTED;

    private static final long SCAN_PERIOD = 15000;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;
    private static final int STATE_DISCONNECTING = 0;

    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String ACTION_DATA_WRITE =
            "com.example.bluetooth.le.ACTION_DATA_WRITE";
    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";
    public final static String EXTRA_DATA_FLOAT =
            "com.example.bluetooth.le.EXTRA_DATA_FLOAT";
    public final static String EXTRA_DATA_DEVICE_ADDRESS =
            "com.example.bluetooth.le.EXTRA_DEVICE_ADDRESS";

    public BluetoothLeService() {
    }

    @Override
    public void onCreate() {
        mHandler = new Handler();

        if(mBluetoothManager == null) {
            mBluetoothManager =
                    (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if(mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if(mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
        }

        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();

        if(mBluetoothAdapter.isEnabled())
            startScanning();

        registerReceiver(mBroadcastReceiver, makeIntentFilter());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mBroadcastReceiver);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public class LocalBinder extends Binder {
        public BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        disconnect();
        return super.onUnbind(intent);
    }

    public void startScanning() {
        Log.d(TAG, "startScanning()");

        /*
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                stopScanning();
            }
        }, SCAN_PERIOD);
        */

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .setReportDelay(1)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                .setNumOfMatches(1)
                .build();
        List<ScanFilter> filters = new ArrayList<>();
        ScanFilter scanFilter = new ScanFilter.Builder()
                .build();
        filters.add(scanFilter);

        mBluetoothLeScanner.startScan(filters, settings, mScanCallback);
    }

    public void stopScanning() {
        Log.i(TAG, "stopScanning()");
        mBluetoothLeScanner.stopScan(mScanCallback);
    }

    public void connect(BluetoothDevice device) {
        if(mConnectionState != STATE_DISCONNECTED)
            return;
        Log.i(TAG, "Trying to connect to " + device.getAddress());
        mBluetoothGatt = device.connectGatt(this, true, mGattCallback);
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
                    connect(mBluetoothGatt.getDevice());
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
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
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

        broadcastWriteUpdate(ACTION_DATA_WRITE, mBluetoothGatt.getDevice().getAddress(), data);
    }

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
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
        sendBroadcast(intent);
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

        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final String deviceAddress)
    {
        final Intent intent = new Intent(action);
        intent.putExtra(EXTRA_DATA_DEVICE_ADDRESS, deviceAddress);
        sendBroadcast(intent);
    }

    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            Log.i(TAG, "onScanResult(): "
                    + result.getDevice().getName()
                    + result.getDevice().getAddress() );

            if(checkUuidOfResult(result) != true)
                return;

            connect(result.getDevice());
            stopScanning();
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            for(ScanResult result: results) {
                Log.i(TAG, "onBatchScanResult(): "
                        + result.getDevice().getName() + " | "
                        + result.getDevice().getAddress() );

                if(checkUuidOfResult(result) != true)
                    continue;

                connect(result.getDevice());
                stopScanning();
                break;
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.i(TAG, "onScanFailed()");
        }
    };

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if(MainActivity.ACTION_START_SCAN.equals(action)) {
                if(mBluetoothLeScanner == null)
                    mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
                if(mBluetoothAdapter.isEnabled())
                    startScanning();
            }
        }
    };

    private IntentFilter makeIntentFilter() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(MainActivity.ACTION_START_SCAN);
        return intentFilter;
    }

    private boolean checkUuidOfResult(ScanResult result) {
        // https://github.com/kiteflo/iBeaconAndroidDemo
        int startByte = 2;
        boolean patternFound = false;
        while(startByte < 6)
        {
            if(((int)result.getScanRecord().getBytes()[startByte + 2] & 0xff) == 0x02
                    &&((int)result.getScanRecord().getBytes()[startByte + 3] & 0xff) == 0x15)
            {
                patternFound = true;
                break;
            }
            startByte++;
        }
        if(!patternFound)
            return false;

        byte[] uuidBytes = new byte[16];
        System.arraycopy(result.getScanRecord().getBytes(), startByte + 4, uuidBytes, 0, 16);

        ByteBuffer bb = ByteBuffer.wrap(uuidBytes);
        UUID uuid = new UUID(bb.getLong(), bb.getLong());
        if(!uuid.equals(UUID_BEACON_BREWCE))
            return false;

        // major
        final int major = (result.getScanRecord().getBytes()[startByte + 20] & 0xff) * 0x100 + (result.getScanRecord().getBytes()[startByte + 21] & 0xff);
        // minor
        final int minor = (result.getScanRecord().getBytes()[startByte + 22] & 0xff) * 0x100 + (result.getScanRecord().getBytes()[startByte + 23] & 0xff);

        Log.i(TAG,"brewce iBeacon detected!");
        Log.i(TAG,"UUID:  " + uuid.toString());
        Log.i(TAG,"major: " + major);
        Log.i(TAG,"minor: " + minor);
        Log.i(TAG,"RSSI:  " + result.getRssi());

        return true;
    }
}
