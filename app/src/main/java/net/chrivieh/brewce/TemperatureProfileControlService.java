package net.chrivieh.brewce;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Set;

import layout.ProgramControlFragment;

public class TemperatureProfileControlService extends Service {

    public static final String TAG = TemperatureProfileControlService.class.getSimpleName();

    public final static String ACTION_COUNTER_EXPIRED =
            "net.chrivieh.brewce.TemperatureProfileControlService.ACTION_COUNTER_EXPIRED";
    public final static String ACTION_COUNTER_CHANGED =
            "net.chrivieh.brewce.TemperatureProfileControlService.ACTION_COUNTER_CHANGED";
    public final static String ACTION_TARGET_TEMPERATURE_CHANGED =
            "net.chrivieh.brewce.TemperatureProfileControlService.ACTION_TARGET_TEMPERATURE_CHANGED";
    public final static String EXTRA_DATA =
            "net.chrivieh.brewce.TemperatureProfileControlService.EXTRA_DATA";

    final Handler mHandler = new Handler();

    private long mElapsed = 0;
    private int mTempProfileIdx = 0;
    private float targetTemp = 0.0f;

    private final IBinder mBinder = new LocalBinder();

    public class LocalBinder extends Binder {
        public TemperatureProfileControlService getService() {
            return TemperatureProfileControlService.this;
        }
    }

    private boolean mRunning = false;
    final Runnable r = new Runnable() {
        @Override
        public void run() {
            mRunning = true;
            long elapsed = SystemClock.uptimeMillis();
            long diff = elapsed - mElapsed;
            mElapsed = elapsed;

            TemperatureProfileData.Setpoint setpoint =
                    TemperatureProfileData.getSetpointOfIdx(mTempProfileIdx);
            setpoint.time -= diff;
            sendCounterChangedBroadcast();

            if(setpoint.time < 0) {
                mTempProfileIdx++;
                diff = setpoint.time;
                if(mTempProfileIdx >= TemperatureProfileData.setpoints.size())
                {
                    sendCounterExpiredBroadcast();
                    mTempProfileIdx = 0;
                    return;
                }
                sendTemperatureChangedBroadcast(
                        TemperatureProfileData.getTemperatureOfIdx(mTempProfileIdx));
            }

            mHandler.postDelayed(this, 250);
        }
    };

    public TemperatureProfileControlService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if(TemperatureProfileData.size() < 1) {
            sendCounterExpiredBroadcast();
            return;
        }

        mElapsed = SystemClock.uptimeMillis();
        registerReceiver(mBroadcastReceiver, makeUpdateIntentFilter());

        Intent intent = new Intent(this, TemperatureControlService.class);
        bindService(intent, mTemperatureControlServiceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDestroy() {
        mHandler.removeCallbacks(r);
        unbindService(mTemperatureControlServiceConnection);
        unregisterReceiver(mBroadcastReceiver);
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive()");
            if(intent.getAction().equals(BluetoothLeService.ACTION_DATA_AVAILABLE)) {
                byte data[] = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);
                float temp = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getFloat();

                if((Math.abs(targetTemp - temp) < 1.0f) && mRunning == false) {
                    mElapsed = SystemClock.uptimeMillis();
                    mHandler.post(r);
                } else {
                    mRunning = false;
                    mHandler.removeCallbacks(r);
                }
            }
        }
    };

    private ServiceConnection mTemperatureControlServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            if(TemperatureProfileData.size() < 1)
                return;
            sendTemperatureChangedBroadcast(
                    TemperatureProfileData.getTemperatureOfIdx(mTempProfileIdx));
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mHandler.removeCallbacks(r);
        }
    };

    private static IntentFilter makeUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(TemperatureProfileControlService.ACTION_COUNTER_CHANGED);
        return intentFilter;
    }

    private void sendCounterChangedBroadcast() {
        Intent intent = new Intent(ACTION_COUNTER_CHANGED);
        sendBroadcast(intent);
    }

    private void sendCounterExpiredBroadcast() {
        Intent intent = new Intent(ACTION_COUNTER_EXPIRED);
        sendBroadcast(intent);
    }

    private void sendTemperatureChangedBroadcast(float temp) {
        Intent intent = new Intent(ACTION_TARGET_TEMPERATURE_CHANGED);
        intent.putExtra(EXTRA_DATA, temp);
        sendBroadcast(intent);
    }
}
