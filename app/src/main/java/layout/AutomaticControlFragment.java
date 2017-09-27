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
import android.widget.TextView;

import net.chrivieh.brewce.BluetoothLeService;
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

    private TemperatureControlService mTemperatureControlService;
    private BluetoothLeService mBluetoothLeService;

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

        intent = new Intent(getActivity(), TemperatureControlService.class);
        getActivity().bindService(intent, mTemperatureControlServiceConnection,
                Context.BIND_AUTO_CREATE);

        getActivity().registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_automatic_control, container, false);
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

    private ServiceConnection mTemperatureControlServiceConnection
            = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            mTemperatureControlService =
                    ((TemperatureControlService.LocalBinder) iBinder).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mTemperatureControlService = null;
        }
    };

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            TextView tvTemp = (TextView) getActivity().findViewById(R.id.tvTemp);

            if(BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                tvTemp.setText("Disconnected");
            }
            else if(BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                byte data[] = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);
                float temp = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getFloat();
                tvTemp.setText(String.format("%3.2f°C", temp));
            }
        }
    };
}
