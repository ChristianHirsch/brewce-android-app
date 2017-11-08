package layout;


import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.codetroopers.betterpickers.hmspicker.HmsPickerBuilder;
import com.codetroopers.betterpickers.hmspicker.HmsPickerDialogFragment;
import com.codetroopers.betterpickers.numberpicker.NumberPickerBuilder;
import com.codetroopers.betterpickers.numberpicker.NumberPickerDialogFragment;

import net.chrivieh.brewce.BluetoothLeService;
import net.chrivieh.brewce.R;
import net.chrivieh.brewce.Setpoint;
import net.chrivieh.brewce.TemperatureProfileControlService;
import net.chrivieh.brewce.TemperatureProfileData;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ProgramControlFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ProgramControlFragment extends Fragment {

    public static final String TAG = ProgramControlFragment.class.getSimpleName();

    public final static String ACTION_COUNTER_CHANGED =
            "net.chrivieh.brewce.layout.ProgramControlFragment.ACTION_COUNTER_CHANGED";
    public final static String EXTRA_DATA_COUNTER =
            "net.chrivieh.brewce.layout.ProgramControlFragment.EXTRA_DATA_COUNTER";
    public final static String EXTRA_DATA_TEMPERATURE =
            "net.chrivieh.brewce.layout.ProgramControlFragment.EXTRA_DATA_TEMPERATURE";

    private FloatingActionButton fab;

    SetpointListAdapter mAdapter;

    private TextView tvTemp;
    private ToggleButton tbOnOffProgramControl;
    private int mCurrentTempIdx = 0;

    public ProgramControlFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment ProgramControlFragment.
     */
    public static ProgramControlFragment newInstance() {
        ProgramControlFragment fragment = new ProgramControlFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
        }

        getActivity().registerReceiver(mBroadcastReceiver, makeUpdateIntentFilter());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_program_control, container, false);
        initializeUiElements(view);

        ListView lv = (ListView) view.findViewById(R.id.listView);
        mAdapter = new SetpointListAdapter();
        lv.setAdapter(mAdapter);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    private void initializeUiElements(View view) {
        tvTemp = (TextView) view.findViewById(R.id.tvTemp);
        tbOnOffProgramControl = (ToggleButton) view.findViewById(R.id.tbOnOffProgram);
        fab = (FloatingActionButton) getActivity().findViewById(R.id.fab);

        tbOnOffProgramControl.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if(b == true) {
                    startAutomaticTemperatureProfileControl();
                }
                else {
                    stopAutomaticTemperatureProfileControl();
                }
            }
        });

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Setpoint setpoint = new Setpoint();
                mAdapter.addSetpoint(setpoint);
                final int idx = mAdapter.getSetpointIdx(setpoint);

                HmsPickerBuilder hpb = new HmsPickerBuilder()
                        .setFragmentManager(getActivity().getSupportFragmentManager())
                        .setStyleResId(R.style.BetterPickersDialogFragment);
                hpb.addHmsPickerDialogHandler(new HmsPickerDialogFragment.HmsPickerDialogHandlerV2() {
                    @Override
                    public void onDialogHmsSet(int reference, boolean isNegative,
                                               int hours, int minutes, int seconds) {
                        int time = ((hours * 60) + minutes) * 60 + seconds;
                        mAdapter.getSetpoint(idx).time = time;
                        mAdapter.notifyDataSetChanged();
                        Log.i(TAG, "" + time);
                    }
                });
                hpb.show();

                NumberPickerBuilder nbp = new NumberPickerBuilder()
                        .setFragmentManager(getActivity().getSupportFragmentManager())
                        .setStyleResId(R.style.BetterPickersDialogFragment)
                        .setLabelText("Temperature.");
                nbp.addNumberPickerDialogHandler(new NumberPickerDialogFragment.NumberPickerDialogHandlerV2() {
                    @Override
                    public void onDialogNumberSet(int reference, BigInteger number, double decimal,
                                                  boolean isNegative, BigDecimal fullNumber) {
                        float temp = fullNumber.floatValue();
                        mAdapter.getSetpoint(idx).temperature = temp;
                        Log.i(TAG, "" + temp);
                    }
                });
                nbp.show();
            }
        });
    }

    private static IntentFilter makeUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(TemperatureProfileControlService.ACTION_COUNTER_CHANGED);
        intentFilter.addAction(TemperatureProfileControlService.ACTION_COUNTER_EXPIRED);
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
                byte data[] = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);
                float temp = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getFloat();
                tvTemp.setText(String.format("%3.1f°C", temp));
            }
            else if(TemperatureProfileControlService.ACTION_COUNTER_CHANGED.equals(action)) {
                Setpoint setpoint = mAdapter.getSetpoint(mCurrentTempIdx);
                setpoint.time = (int)intent.getLongExtra(
                        TemperatureProfileControlService.EXTRA_DATA, 0);
                mAdapter.notifyDataSetChanged();
            }
        }
    };

    private ServiceConnection mTemperatureProfileControlServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.d(TAG, "onServiceConnected()");
            Setpoint setpoint = mAdapter.getSetpoint(mCurrentTempIdx);
            final Intent intent = new Intent(ACTION_COUNTER_CHANGED);
            intent.putExtra(EXTRA_DATA_COUNTER, setpoint.time);
            getActivity().sendBroadcast(intent);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {

        }
    };

    private void startAutomaticTemperatureProfileControl() {
        Intent intent = new Intent(getActivity(), TemperatureProfileControlService.class);
        getActivity().bindService(intent, mTemperatureProfileControlServiceConnection,
                Context.BIND_AUTO_CREATE);
    }

    private void stopAutomaticTemperatureProfileControl() {
        getActivity().unbindService(mTemperatureProfileControlServiceConnection);
    }

    private class SetpointListAdapter extends BaseAdapter {

        private LayoutInflater mInflator;

        public SetpointListAdapter() {
            super();
            mInflator = ProgramControlFragment.this.getActivity().getLayoutInflater();
        }

        public void addSetpoint(TemperatureProfileData.Setpoint setpoint) {
            if(!TemperatureProfileData.setpoints.contains(setpoint))
                TemperatureProfileData.setpoints.add(setpoint);
        }

        public void addSetpoint(float temperature, long time) {
            TemperatureProfileData.Setpoint setpoint = new TemperatureProfileData.Setpoint();
            setpoint.temperature = temperature;
            setpoint.time = time;
            if(!TemperatureProfileData.setpoints.contains(setpoint))
                TemperatureProfileData.setpoints.add(setpoint);
        }

        public int getSetpointIdx(TemperatureProfileData.Setpoint setpoint) {
            return TemperatureProfileData.setpoints.indexOf(setpoint);
        }

        public TemperatureProfileData.Setpoint getSetpoint(int pos) {
            return TemperatureProfileData.setpoints.get(pos);
        }

        public void clear() {
            TemperatureProfileData.setpoints.clear();
        }

        @Override
        public int getCount() {
             return TemperatureProfileData.setpoints.size();
         }

        @Override
        public Object getItem(int pos) {
             return getSetpoint(pos);
        }

        @Override
        public long getItemId(int pos) {
            return pos;
        }

        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;

            if(view == null) {
                view = mInflator.inflate(R.layout.listitem_temperature, null);
                viewHolder = new ViewHolder();
                viewHolder.temperature = (TextView) view.findViewById(R.id.temperature_setpoint);
                viewHolder.time = (TextView) view.findViewById(R.id.temperature_time);
                view.setTag(viewHolder);
            }
            else {
                viewHolder = (ViewHolder) view.getTag();
            }

            TemperatureProfileData.Setpoint setpoint = TemperatureProfileData.setpoints.get(i);
            viewHolder.temperature.setText(""  + setpoint.temperature);
            viewHolder.time.setText(""  + DateUtils.formatElapsedTime(setpoint.time));

            return view;
        }
    }

    private class ViewHolder {
        TextView temperature;
        TextView time;
    }
}
