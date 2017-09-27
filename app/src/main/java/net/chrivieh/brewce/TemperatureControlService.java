package net.chrivieh.brewce;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.attribute.AclEntry;

public class TemperatureControlService extends Service {

    public static final String TAG = TemperatureControlService.class.getSimpleName();

    private final IBinder mBinder = new LocalBinder();

    public class LocalBinder extends Binder {
        public TemperatureControlService getService() {
            return TemperatureControlService.this;
        }
    }

    private BluetoothLeService mBluetoothLeService;

    private PIDController mPIDController;

    public TemperatureControlService() {
        mPIDController = new PIDController();

        mPIDController.setKp(1.0f);
        mPIDController.setKi(0.0f);
        mPIDController.setKd(0.0f);

        mPIDController.setWindupLimit(10.0f);
        mPIDController.setLowerLimit(0.0f);
        mPIDController.setUpperLimit(255.0f);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        // bind to BluetoothLeService
        Intent intent = new Intent(this, BluetoothLeService.class);
        bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);

        // register for updates from BluetoothLeService
        registerReceiver(mGattUpdateReceiver,
                new IntentFilter(BluetoothLeService.ACTION_DATA_AVAILABLE));
    }

    final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) iBinder).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            byte data[] = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);
            float temp = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getFloat();

            float controlEffort = mPIDController.calcControlEffort(temp, 30.0f);
            Log.i(TAG, "controlEffort(" + temp + ", " + 30.0f + ") = " + controlEffort);

            byte[] wData = {0x02, 0x00};
            wData[1] = (byte) controlEffort;
            mBluetoothLeService.write(wData);
        }
    };
}
