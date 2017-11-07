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

import layout.ProgramControlFragment;

public class TemperatureProfileControlService extends Service {

    public static final String TAG = TemperatureProfileControlService.class.getSimpleName();

    public final static String ACTION_COUNTER_EXPIRED =
            "net.chrivieh.brewce.TemperatureProfileControlService.ACTION_COUNTER_EXPIRED";
    public final static String ACTION_COUNTER_CHANGED =
            "net.chrivieh.brewce.TemperatureProfileControlService.ACTION_COUNTER_CHANGED";
    public final static String EXTRA_DATA =
            "net.chrivieh.brewce.TemperatureProfileControlService.EXTRA_DATA";

    final Handler mHandler = new Handler();

    private boolean mRunning = false;
    final Runnable r = new Runnable() {
        @Override
        public void run() {
            long elapsed = SystemClock.uptimeMillis();
            long diff = elapsed - mElapsed;
            mElapsed = elapsed;

            Intent intent;

            mCounterMillis -= diff;
            intent = new Intent(ACTION_COUNTER_CHANGED);
            intent.putExtra(EXTRA_DATA, (long) mCounterMillis / 1000);
            sendBroadcast(intent);

            if(mCounterMillis < 0) {
                intent = new Intent(ACTION_COUNTER_EXPIRED);
                sendBroadcast(intent);
                return;
            }

            mHandler.postDelayed(this, 250);

            Log.d(TAG, "r.run(): " + diff + " "  + mCounterMillis);
        }
    };

    private final IBinder mBinder = new LocalBinder();

    private long mElapsed = 0;
    private long mCounterMillis = 0;
    private float targetTemp = 0.0f;

    public class LocalBinder extends Binder {
        public TemperatureProfileControlService getService() {
            return TemperatureProfileControlService.this;
        }
    }

    public TemperatureProfileControlService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        registerReceiver(mBroadcastReceiver, makeUpdateIntentFilter());
        Log.d(TAG, "onCreate()");
    }

    @Override
    public void onDestroy() {
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
                    mRunning = true;
                    mElapsed = SystemClock.uptimeMillis();
                    mHandler.post(r);
                } else {
                    mRunning = false;
                    mHandler.removeCallbacks(r);
                }
            }
            else if(intent.getAction().equals(ProgramControlFragment.ACTION_COUNTER_CHANGED)) {
                Log.d(TAG, "onReceive(): ProgramControlFragment.ACTION_COUNTER_CHANGED");
                mCounterMillis = intent.getLongExtra(ProgramControlFragment.EXTRA_DATA_COUNTER, 0) * 1000;
                mHandler.post(r);
            }
        }
    };

    private ServiceConnection mTemperatureControlServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {

        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {

        }
    };

    private static IntentFilter makeUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(TemperatureProfileControlService.ACTION_COUNTER_CHANGED);
        return intentFilter;
    }
}
