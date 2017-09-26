package layout;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_manual_control, container, false);
    }

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            ToggleButton btn = (ToggleButton) getView().findViewById(R.id.tbOnOff);

            if(BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                btn.setEnabled(true);
            } else if(BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                btn.setEnabled(false);
            }
        }
    };
}
