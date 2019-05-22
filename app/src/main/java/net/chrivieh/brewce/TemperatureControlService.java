package net.chrivieh.brewce;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import layout.AutomaticControlFragment;
import layout.TemperatureChartFragment;

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

    public final static String PID_KP_VALUE_CHANGED =
            "net.chrivieh.brewce.TemperatureControlService.PID_KP_VALUE_CHANGED";
    public final static String PID_KI_VALUE_CHANGED =
            "net.chrivieh.brewce.TemperatureControlService.PID_KI_VALUE_CHANGED";
    public final static String PID_KD_VALUE_CHANGED =
            "net.chrivieh.brewce.TemperatureControlService.PID_KD_VALUE_CHANGED";
    public final static String PID_KP_VALUE =
            "net.chrivieh.brewce.TemperatureControlService.PID_KP_VALUE";
    public final static String PID_KI_VALUE =
            "net.chrivieh.brewce.TemperatureControlService.PID_KI_VALUE";
    public final static String PID_KD_VALUE =
            "net.chrivieh.brewce.TemperatureControlService.PID_KD_VALUE";

    private BluetoothLeService mBluetoothLeService;

    private PIDController mPIDController;

    public TemperatureControlService() {
        mPIDController = new PIDController();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        final float    kp = preferences.getFloat(PID_KP_VALUE, 25.5f);
        final float    ki = preferences.getFloat(PID_KI_VALUE, 5.0f);
        final float    kd = preferences.getFloat(PID_KD_VALUE, 2.5f);

        mPIDController.setKp(kp);
        mPIDController.setKi(ki);
        mPIDController.setKd(kd);

        mPIDController.setWindupLimit(10.0f);
        mPIDController.setLowerLimit(0.0f);
        mPIDController.setUpperLimit(255.0f);

        mPIDController.setSetpoint(0);

        // bind to BluetoothLeService
        Intent intent = new Intent(this, BluetoothLeService.class);
        bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(AutomaticControlFragment.ACTION_TARGET_TEMPERATURE_CHANGED);
        intentFilter.addAction(TemperatureProfileControlService.ACTION_TARGET_TEMPERATURE_CHANGED);
        intentFilter.addAction(PID_KP_VALUE_CHANGED);
        intentFilter.addAction(PID_KI_VALUE_CHANGED);
        intentFilter.addAction(PID_KD_VALUE_CHANGED);

        // register for updates from BluetoothLeService
        registerReceiver(mBroadcastReceiver,
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
        unregisterReceiver(mBroadcastReceiver);
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case BluetoothLeService.ACTION_DATA_AVAILABLE:
                    byte data[] = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);
                    float temp = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getFloat();
                    if(temp < -273.15f || temp > 120.0f)
                        return;

                    float controlEffort = mPIDController.calcControlEffort(temp);

                    byte[] wData = {0x02, 0x00};
                    wData[1] = (byte) controlEffort;
                    mBluetoothLeService.write(wData);

                    Intent actionIntent = new Intent(ACTION_CONTROL_EFFORT_CHANGED);
                    actionIntent.putExtra(EXTRA_DATA, (int)(Math.round(controlEffort)));
                    sendBroadcast(actionIntent);

                    break;
                case AutomaticControlFragment.ACTION_TARGET_TEMPERATURE_CHANGED:
                    float targetTemp = intent.getFloatExtra(AutomaticControlFragment.EXTRA_DATA, 0.0f);
                    mPIDController.setSetpoint(targetTemp);
                    break;
                case TemperatureProfileControlService.ACTION_TARGET_TEMPERATURE_CHANGED:
                    targetTemp = intent.getFloatExtra(TemperatureProfileControlService.EXTRA_DATA, 0.0f);
                    mPIDController.setSetpoint(targetTemp);
                    break;
                case PID_KP_VALUE_CHANGED:
                    float value = intent.getFloatExtra(EXTRA_DATA, 0.0f);
                    PIDController.setKp(value);
                    Log.d(TAG, "kp = " + value);
                    break;
                case PID_KI_VALUE_CHANGED:
                    value = intent.getFloatExtra(EXTRA_DATA, 0.0f);
                    PIDController.setKi(value);
                    Log.d(TAG, "ki = " + value);
                    break;
                case PID_KD_VALUE_CHANGED:
                    value = intent.getFloatExtra(EXTRA_DATA, 0.0f);
                    PIDController.setKd(value);
                    Log.d(TAG, "kd = " + value);
                    break;
            }
        }
    };
}
