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
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ToggleButton;

import net.chrivieh.brewce.BluetoothLeService;
import net.chrivieh.brewce.MainActivity;
import net.chrivieh.brewce.R;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ManualControlFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ManualControlFragment extends Fragment {

    private BluetoothLeService mBluetoothLeService;

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

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            ToggleButton btn =
                    (ToggleButton) getActivity().findViewById(R.id.tbOnOff);
            SeekBar sb = (SeekBar) getActivity().findViewById(R.id.sbPower);

            if(BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                btn.setEnabled(true);
                sb.setEnabled(true);
            } else if(BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                btn.setEnabled(false);
                sb.setEnabled(false);
            }
        }
    };

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

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        return intentFilter;
    }

    private void initializeUiElements(View view) {
        final ToggleButton btn = (ToggleButton) view.findViewById(R.id.tbOnOff);
        final SeekBar sb = (SeekBar) view.findViewById(R.id.sbPower);

        if (btn != null) {
            btn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                    byte[] data = {0, 0};
                    if (b == true) {
                        data[0] = 0x01;
                    } else {
                        data[0] = 0x00;
                    }
                    data[1] = (byte) sb.getProgress();
                    mBluetoothLeService.write(data);
                }
            });
        }

        if (sb != null) {
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int i, boolean b) {

                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    byte[] data = {0, 0};
                    if (btn.isChecked())
                        data[0] = 0x01;
                    else
                        data[0] = 0x00;
                    data[1] = (byte) seekBar.getProgress();
                    mBluetoothLeService.write(data);
                }
            });
        }
    }
}
