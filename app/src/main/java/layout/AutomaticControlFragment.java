package layout;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ToggleButton;

import net.chrivieh.brewce.BluetoothLeService;
import net.chrivieh.brewce.MqttGatewayService;
import net.chrivieh.brewce.R;
import net.chrivieh.brewce.TemperatureControlService;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link AutomaticControlFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class AutomaticControlFragment extends Fragment {

    public final static String TAG = AutomaticControlFragment.class.getSimpleName();

    private TemperatureControlService mTemperatureControlService;
    private BluetoothLeService mBluetoothLeService;
    private MqttGatewayService mMqttGatewayService;

    private TextView tvTemp;
    private TextView tvTargetTemp;
    private SeekBar sbTargetTemp;
    private ToggleButton tbStartStopp;
    private TextView tvPower;
    private ProgressBar pbPower;

    public final static String ACTION_TARGET_TEMPERATURE_CHANGED =
            "net.chrivieh.brewce.layout.AutomaticControlFragment.ACTION_TARGET_TEMPERATURE_CHANGED";
    public final static String EXTRA_DATA =
            "net.chrivieh.brewce.layout.AutomaticControlFragment.EXTRA_DATA";

    public AutomaticControlFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment ManualControlFragment.
     */
    public static AutomaticControlFragment newInstance() {
        AutomaticControlFragment fragment = new AutomaticControlFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
        }

        Intent intent = new Intent(getActivity(), BluetoothLeService.class);
        getActivity().bindService(intent, mBluetoothLeServiceConnection,
                Context.BIND_AUTO_CREATE);

        intent = new Intent(getActivity(), MqttGatewayService.class);
        getActivity().bindService(intent, mMqttGatewayServiceConnection,
                Context.BIND_AUTO_CREATE);

        getActivity().registerReceiver(mBroadcastReceiver, makeUpdateIntentFilter());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_automatic_control, container, false);
        initializeUIElements(rootView);
        return rootView;
    }

    private ServiceConnection mBluetoothLeServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            mBluetoothLeService =
                    ((BluetoothLeService.LocalBinder) iBinder).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    private ServiceConnection mMqttGatewayServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            mMqttGatewayService =
                    ((MqttGatewayService.LocalBinder) iBinder).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mMqttGatewayService = null;
        }
    };

    private ServiceConnection mTemperatureControlServiceConnection
            = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            mTemperatureControlService =
                    ((TemperatureControlService.LocalBinder) iBinder).getService();
            final Intent intent = new Intent(ACTION_TARGET_TEMPERATURE_CHANGED);
            intent.putExtra(EXTRA_DATA, (float)sbTargetTemp.getProgress());
            getActivity().sendBroadcast(intent);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mTemperatureControlService = null;
        }
    };

    private static IntentFilter makeUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(TemperatureControlService.ACTION_CONTROL_EFFORT_CHANGED);
        return intentFilter;
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if(BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                tvTemp.setText("Disconnected");
            }
            else if(BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                float temp = intent.getFloatExtra(BluetoothLeService.EXTRA_DATA_FLOAT, 0.0f);
                tvTemp.setText(String.format("%3.1f°C", temp));
            }
            else if(TemperatureControlService.ACTION_CONTROL_EFFORT_CHANGED.equals(action)) {
                int controlEffort = intent.getIntExtra(TemperatureControlService.EXTRA_DATA, 0);
                int power = (int)Math.round(((3500/250) * controlEffort) / 100);
                tvPower.setText("" + power * 100);
                pbPower.setProgress(controlEffort);
            }
        }
    };

    private void initializeUIElements(View view) {
        tvTemp = (TextView) view.findViewById(R.id.tvTemp);
        tvTargetTemp = (TextView) view.findViewById(R.id.tvTargetTemp);
        sbTargetTemp = (SeekBar) view.findViewById(R.id.sbTargetTemp);
        tbStartStopp = (ToggleButton) view.findViewById(R.id.tbStartStop);
        tvPower = (TextView) view.findViewById(R.id.tvControlEffort);
        pbPower = (ProgressBar) view.findViewById(R.id.pbPower);

        tbStartStopp.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if(b == true) {
                    startAutomaticTemperatureControl();
                }
                else {
                    stopAutomaticTemperatureControl();
                }
            }
        });

        sbTargetTemp.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                tvTargetTemp.setText(String.format("%3.1f°C", ((float)seekBar.getProgress())));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                final Intent intent = new Intent(ACTION_TARGET_TEMPERATURE_CHANGED);
                intent.putExtra(EXTRA_DATA, (float)seekBar.getProgress());
                getActivity().sendBroadcast(intent);
            }
        });

    }

    private void startAutomaticTemperatureControl() {
        Intent intent = new Intent(getActivity(), TemperatureControlService.class);
        getActivity().bindService(intent, mTemperatureControlServiceConnection,
                Context.BIND_AUTO_CREATE);
    }

    private void stopAutomaticTemperatureControl() {
        getActivity().unbindService(mTemperatureControlServiceConnection);
        tvPower.setText("" + 0);
        pbPower.setProgress(0);
        byte[] data = {0, 0};
        mBluetoothLeService.write(data);
    }
}
