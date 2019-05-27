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

    private Handler mHandler;

    private static final long SCAN_PERIOD = 15000;

    private SensorNode mSensorNode;
    private ActuatorNode mActuatorNode;

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
        if(mSensorNode != null)
            mSensorNode.disconnect();
        if(mActuatorNode != null)
            mActuatorNode.disconnect();
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

    public void write(byte[] data) {
        if(mActuatorNode == null)
            return;

        mActuatorNode.write(data);
    }

    public ActuatorNode getActuatorNode() {
        return mActuatorNode;
    }

    public Boolean isActuatorNodeConnected() {
        if(mActuatorNode == null)
            return false;
        return mActuatorNode.isConnected();
    }

    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            Log.i(TAG, "onScanResult(): "
                    + result.getDevice().getName()
                    + result.getDevice().getAddress() );
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            for(ScanResult result: results) {
                Log.i(TAG, "onBatchScanResult(): "
                        + result.getDevice().getName() + " | "
                        + result.getDevice().getAddress() );

                int major = checkUuidOfResult(result);
                if(major < 0)
                    continue;

                if(major == 1 && mActuatorNode == null) {
                    mActuatorNode = new ActuatorNode(getBaseContext(), result.getDevice());
                    mActuatorNode.connect();
                }
                else if(major == 2 && mSensorNode == null) {
                    mSensorNode = new SensorNode(getBaseContext(), result.getDevice());
                    mSensorNode.connect();
                }

                if(mSensorNode != null && mActuatorNode != null) {
                    stopScanning();
                    break;
                }
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

    private int checkUuidOfResult(ScanResult result) {
        // https://github.com/kiteflo/iBeaconAndroidDemo
        if(result.getScanRecord().getBytes().length < 26)
            return -1;

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
            return -1;

        byte[] uuidBytes = new byte[16];
        System.arraycopy(result.getScanRecord().getBytes(), startByte + 4, uuidBytes, 0, 16);

        ByteBuffer bb = ByteBuffer.wrap(uuidBytes);
        UUID uuid = new UUID(bb.getLong(), bb.getLong());
        if(!uuid.equals(UUID_BEACON_BREWCE))
            return -1;

        // major
        final int major = (result.getScanRecord().getBytes()[startByte + 20] & 0xff) * 0x100 + (result.getScanRecord().getBytes()[startByte + 21] & 0xff);
        // minor
        final int minor = (result.getScanRecord().getBytes()[startByte + 22] & 0xff) * 0x100 + (result.getScanRecord().getBytes()[startByte + 23] & 0xff);

        Log.i(TAG,"brewce iBeacon detected!");
        Log.i(TAG,"UUID:  " + uuid.toString());
        Log.i(TAG,"major: " + major);
        Log.i(TAG,"minor: " + minor);
        Log.i(TAG,"RSSI:  " + result.getRssi());

        return major;
    }
}
