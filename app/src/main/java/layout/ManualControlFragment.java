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
import android.widget.SeekBar;
import android.widget.ToggleButton;

import net.chrivieh.brewce.ActuatorNode;
import net.chrivieh.brewce.BluetoothLeService;
import net.chrivieh.brewce.R;

import java.util.concurrent.CompletableFuture;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ManualControlFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ManualControlFragment extends Fragment {

    private BluetoothLeService mBluetoothLeService;

    private ToggleButton tbOnOff;
    private SeekBar sbPower;

    public ManualControlFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment ManualControlFragment.
     */
    public static ManualControlFragment newInstance() {
        ManualControlFragment fragment = new ManualControlFragment();
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

        getActivity().registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_manual_control, container, false);
        initializeUiElements(view);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if(mBluetoothLeService == null)
            return;
        tbOnOff.setEnabled(mBluetoothLeService.isActuatorNodeConnected());
        sbPower.setEnabled(mBluetoothLeService.isActuatorNodeConnected());
    }

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if(ActuatorNode.ACTION_GATT_CONNECTED.equals(action)) {
                tbOnOff.setEnabled(true);
                sbPower.setEnabled(true);
            } else if(ActuatorNode.ACTION_GATT_DISCONNECTED.equals(action)) {
                tbOnOff.setEnabled(false);
                sbPower.setEnabled(false);
            }
        }
    };

    private ServiceConnection mBluetoothLeServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            mBluetoothLeService =
                    ((BluetoothLeService.LocalBinder) iBinder).getService();
            tbOnOff.setEnabled(mBluetoothLeService.isActuatorNodeConnected());
            sbPower.setEnabled(mBluetoothLeService.isActuatorNodeConnected());
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ActuatorNode.ACTION_GATT_CONNECTED);
        intentFilter.addAction(ActuatorNode.ACTION_GATT_DISCONNECTED);
        return intentFilter;
    }

    private void initializeUiElements(View view) {
        tbOnOff = (ToggleButton) view.findViewById(R.id.tbOnOff);
        sbPower = (SeekBar) view.findViewById(R.id.sbPower);

        tbOnOff.setEnabled(false);
        tbOnOff.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                byte[] data = {0, 0};
                if (b == true) {
                    data[0] = 0x01;
                } else {
                    data[0] = 0x00;
                }
                data[1] = (byte) sbPower.getProgress();
                mBluetoothLeService.write(data);
            }
        });

        sbPower.setEnabled(false);
        sbPower.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                byte[] data = {0, 0};
                if (tbOnOff.isChecked())
                    data[0] = 0x01;
                else
                    data[0] = 0x00;
                data[1] = (byte) seekBar.getProgress();
                mBluetoothLeService.write(data);
            }
        });

    }
}
