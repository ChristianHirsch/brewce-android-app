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

import layout.AutomaticControlFragment;

public class TemperatureControlService extends Service {

    public static final String TAG = TemperatureControlService.class.getSimpleName();

    private final IBinder mBinder = new LocalBinder();

    public class LocalBinder extends Binder {
        public TemperatureControlService getService() {
            return TemperatureControlService.this;
        }
    }

    public final static String ACTION_CONTROL_EFFORT_CHANGED =
            "net.chrivieh.brewce.TemperatureControlService.ACTION_CONTROL_EFFORT_CHANGED";
    public final static String EXTRA_DATA =
            "net.chrivieh.brewce.TemperatureControlService.EXTRA_DATA";

    private BluetoothLeService mBluetoothLeService;

    private PIDController mPIDController;

    public TemperatureControlService() {
        mPIDController = new PIDController();

        mPIDController.setKp(25.5f);

        mPIDController.setKi(5.0f);
        mPIDController.setKd(2.5f);

        mPIDController.setWindupLimit(10.0f);
        mPIDController.setLowerLimit(0.0f);
        mPIDController.setUpperLimit(255.0f);

        mPIDController.setSetpoint(0);
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

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(AutomaticControlFragment.ACTION_TARGET_TEMPERATURE_CHANGED);
        // register for updates from BluetoothLeService
        registerReceiver(mGattUpdateReceiver,
                new IntentFilter(intentFilter));
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        unregisterReceiver(mGattUpdateReceiver);
    }

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals(BluetoothLeService.ACTION_DATA_AVAILABLE)) {
                byte data[] = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);
                float temp = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getFloat();

                float controlEffort = mPIDController.calcControlEffort(temp);

                byte[] wData = {0x02, 0x00};
                wData[1] = (byte) controlEffort;
                mBluetoothLeService.write(wData);

                Intent actionIntent = new Intent(ACTION_CONTROL_EFFORT_CHANGED);
                actionIntent.putExtra(EXTRA_DATA, (int)(Math.round(controlEffort)));
                sendBroadcast(actionIntent);
            }
            else if(intent.getAction().equals(AutomaticControlFragment.ACTION_TARGET_TEMPERATURE_CHANGED)) {
                int targetTemp = intent.getIntExtra(AutomaticControlFragment.EXTRA_DATA, 0);
                mPIDController.setSetpoint((float)targetTemp);
            }
        }
    };
}
