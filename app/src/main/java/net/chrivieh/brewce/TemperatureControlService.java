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
import android.preference.PreferenceManager;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import layout.AutomaticControlFragment;

public class TemperatureControlService extends Service {

    public static final String TAG = TemperatureControlService.class.getSimpleName();

    public static final String PID_P_VALUE = "PID_P_VALUE";
    public static final String PID_I_VALUE = "PID_I_VALUE";
    public static final String PID_D_VALUE = "PID_D_VALUE";

    private final IBinder mBinder = new LocalBinder();

    public class LocalBinder extends Binder {
        public TemperatureControlService getService() {
            return TemperatureControlService.this;
        }
    }

    public final static String ACTION_PID_P_VALUE_CHANGED =
            "net.chrivieh.brewce.TemperatureControlService.ACTION_PID_P_VALUE_CHANGED";
    public final static String ACTION_PID_I_VALUE_CHANGED =
            "net.chrivieh.brewce.TemperatureControlService.ACTION_PID_I_VALUE_CHANGED";
    public final static String ACTION_PID_D_VALUE_CHANGED =
            "net.chrivieh.brewce.TemperatureControlService.ACTION_PID_D_VALUE_CHANGED";

    public final static String ACTION_CONTROL_EFFORT_CHANGED =
            "net.chrivieh.brewce.TemperatureControlService.ACTION_CONTROL_EFFORT_CHANGED";
    public final static String EXTRA_DATA =
            "net.chrivieh.brewce.TemperatureControlService.EXTRA_DATA";
    public final static String EXTRA_DATA_P_VALUE =
            "net.chrivieh.brewce.TemperatureControlService.EXTRA_DATA_P_VALUE";
    public final static String EXTRA_DATA_I_VALUE =
            "net.chrivieh.brewce.TemperatureControlService.EXTRA_DATA_I_VALUE";
    public final static String EXTRA_DATA_D_VALUE =
            "net.chrivieh.brewce.TemperatureControlService.EXTRA_DATA_D_VALUE";

    private NodeScannerService mNodeScannerService;

    private PIDController mPIDController;

    public TemperatureControlService() {
        mPIDController = new PIDController();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        final float kP = preferences.getFloat(PID_P_VALUE, 25.5f);
        final float kI = preferences.getFloat(PID_I_VALUE,  5.0f);
        final float kD = preferences.getFloat(PID_D_VALUE,  2.5f);

        mPIDController.setKp(kP);
        mPIDController.setKi(kI);
        mPIDController.setKd(kD);

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
        // bind to NodeScannerService
        Intent intent = new Intent(this, NodeScannerService.class);
        bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(SensorNode.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(AutomaticControlFragment.ACTION_TARGET_TEMPERATURE_CHANGED);
        intentFilter.addAction(TemperatureProfileControlService.ACTION_TARGET_TEMPERATURE_CHANGED);
        intentFilter.addAction(TemperatureControlService.ACTION_PID_P_VALUE_CHANGED);
        intentFilter.addAction(TemperatureControlService.ACTION_PID_I_VALUE_CHANGED);
        intentFilter.addAction(TemperatureControlService.ACTION_PID_D_VALUE_CHANGED);
        // register for updates from NodeScannerService
        registerReceiver(mBroadcastReceiver,
                new IntentFilter(intentFilter));
    }

    final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            mNodeScannerService = ((NodeScannerService.LocalBinder) iBinder).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mNodeScannerService = null;
        }
    };

    @Override
    public void onDestroy() {
        super.onDestroy();
        byte[] data = {0, 0};
        mNodeScannerService.write(data);
        unbindService(mServiceConnection);
        unregisterReceiver(mBroadcastReceiver);
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals(SensorNode.ACTION_DATA_AVAILABLE)) {
                byte data[] = intent.getByteArrayExtra(SensorNode.EXTRA_DATA);
                float temp = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getFloat();
                if(temp < -273.15f || temp > 150.0f)
                    return;

                float controlEffort = mPIDController.calcControlEffort(temp);

                byte[] wData = {0x02, 0x00};
                wData[1] = (byte) controlEffort;
                mNodeScannerService.write(wData);

                Intent actionIntent = new Intent(ACTION_CONTROL_EFFORT_CHANGED);
                actionIntent.putExtra(EXTRA_DATA, (int)(Math.round(controlEffort)));
                sendBroadcast(actionIntent);
            }
            else if(intent.getAction().equals(AutomaticControlFragment.ACTION_TARGET_TEMPERATURE_CHANGED)) {
                float targetTemp = intent.getFloatExtra(AutomaticControlFragment.EXTRA_DATA, 0.0f);
                mPIDController.setSetpoint(targetTemp);
            }
            else if(intent.getAction().equals(TemperatureProfileControlService.ACTION_TARGET_TEMPERATURE_CHANGED)) {
                float targetTemp = intent.getFloatExtra(TemperatureProfileControlService.EXTRA_DATA, 0.0f);
                mPIDController.setSetpoint(targetTemp);
            }
            else if(intent.getAction().equals(ACTION_PID_P_VALUE_CHANGED)) {
                float kP = intent.getFloatExtra(EXTRA_DATA_P_VALUE, 0.0f);
                mPIDController.setKp(kP);
                Log.d(TAG, "P value changed to " + kP);
            }
            else if(intent.getAction().equals(ACTION_PID_I_VALUE_CHANGED)) {
                float kI = intent.getFloatExtra(EXTRA_DATA_I_VALUE, 0.0f);
                mPIDController.setKi(kI);
                Log.d(TAG, "I value changed to " + kI);
            }
            else if(intent.getAction().equals(ACTION_PID_D_VALUE_CHANGED)) {
                float kD = intent.getFloatExtra(EXTRA_DATA_D_VALUE, 0.0f);
                mPIDController.setKd(kD);
                Log.d(TAG, "D value changed to " + kD);
            }
        }
    };
}
