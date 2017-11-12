package layout;


import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
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
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.codetroopers.betterpickers.hmspicker.HmsPickerBuilder;
import com.codetroopers.betterpickers.hmspicker.HmsPickerDialogFragment;
import com.codetroopers.betterpickers.numberpicker.NumberPickerBuilder;
import com.codetroopers.betterpickers.numberpicker.NumberPickerDialogFragment;

import net.chrivieh.brewce.BluetoothLeService;
import net.chrivieh.brewce.R;
import net.chrivieh.brewce.TemperatureProfileControlService;
import net.chrivieh.brewce.TemperatureProfileData;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link TemperatureProfileControlFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class TemperatureProfileControlFragment extends Fragment {

    public static final String TAG = TemperatureProfileControlFragment.class.getSimpleName();

    public final static String ACTION_COUNTER_CHANGED =
            "net.chrivieh.brewce.layout.TemperatureProfileControlFragment.ACTION_COUNTER_CHANGED";
    public final static String EXTRA_DATA_COUNTER =
            "net.chrivieh.brewce.layout.TemperatureProfileControlFragment.EXTRA_DATA_COUNTER";
    public final static String EXTRA_DATA_TEMPERATURE =
            "net.chrivieh.brewce.layout.TemperatureProfileControlFragment.EXTRA_DATA_TEMPERATURE";

    private FloatingActionButton fab;

    SetpointListAdapter mAdapter;

    private TextView tvTemp;
    private TextView tvTargetTemp;
    private ToggleButton tbOnOffProgramControl;
    private ListView mListView;
    private int mCurrentTempIdx = 0;
    private boolean isBound = false;

    public TemperatureProfileControlFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment TemperatureProfileControlFragment.
     */
    public static TemperatureProfileControlFragment newInstance() {
        TemperatureProfileControlFragment fragment = new TemperatureProfileControlFragment();
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
        View view = inflater.inflate(R.layout.fragment_temperature_profile_control, container, false);
        initializeUiElements(view);

        mListView = (ListView) view.findViewById(R.id.listView);
        mAdapter = new SetpointListAdapter();
        mListView.setAdapter(mAdapter);
        mListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {

                final int positionToEdit = i;

                PopupMenu pm = new PopupMenu(getActivity(), view);
                pm.getMenuInflater().inflate(
                        R.menu.temperature_profile_element_popup_menu, pm.getMenu());

                pm.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem menuItem) {
                        switch (menuItem.getItemId()) {
                            case R.id.editItem:
                                showEditTemperatureProfileSetpoint(positionToEdit);
                                break;
                            case R.id.deleteItem:
                                showDeleteTemperatureProfileSetpoint(positionToEdit);
                                break;
                            default:
                                return false;
                        }
                        return true;
                    }
                });

                pm.show();
                return true;
            }
        });

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
        tvTargetTemp = (TextView) view.findViewById(R.id.tvTargetTemp);
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

                TemperatureProfileData.Setpoint setpoint = new TemperatureProfileData.Setpoint();
                setpoint.temperature = 0;
                setpoint.time = 0;
                mAdapter.addSetpoint(setpoint);
                int idx = mAdapter.getSetpointIdx(setpoint);
                showEditTemperatureProfileSetpoint(idx);
            }
        });
    }

    private static IntentFilter makeUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(TemperatureProfileControlService.ACTION_COUNTER_CHANGED);
        intentFilter.addAction(TemperatureProfileControlService.ACTION_COUNTER_EXPIRED);
        intentFilter.addAction(TemperatureProfileControlService.ACTION_TARGET_TEMPERATURE_CHANGED);
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
                mAdapter.notifyDataSetChanged();
            }
            else if(TemperatureProfileControlService.ACTION_COUNTER_EXPIRED.equals(action)) {
                stopAutomaticTemperatureProfileControl();
            } else if(TemperatureProfileControlService.ACTION_TARGET_TEMPERATURE_CHANGED.equals(action)) {
                float targetTemp = intent.getFloatExtra(
                        TemperatureProfileControlService.EXTRA_DATA, 0);
                tvTargetTemp.setText(String.format("%3.1f°C", targetTemp));
            }
        }
    };

    private ServiceConnection mTemperatureProfileControlServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.d(TAG, "onServiceConnected()");
            isBound = true;
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
        tbOnOffProgramControl.setChecked(false);
        tvTargetTemp.setText("0.0°C");
        if(isBound)
            getActivity().unbindService(mTemperatureProfileControlServiceConnection);
        isBound = false;
    }

    private class SetpointListAdapter extends BaseAdapter {

        private LayoutInflater mInflator;

        public SetpointListAdapter() {
            super();
            mInflator = TemperatureProfileControlFragment.this.getActivity().getLayoutInflater();
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
                viewHolder.tvTemperature = (TextView) view.findViewById(R.id.temperature_setpoint);
                viewHolder.tvTime = (TextView) view.findViewById(R.id.temperature_time);
                view.setTag(viewHolder);
            }
            else {
                viewHolder = (ViewHolder) view.getTag();
            }

            TemperatureProfileData.Setpoint setpoint = TemperatureProfileData.setpoints.get(i);
            viewHolder.tvTemperature.setText(""  + setpoint.temperature);
            viewHolder.tvTime.setText(""  + DateUtils.formatElapsedTime(setpoint.time / 1000));
            switch (setpoint.status) {
                case ACTIVE:
                    viewHolder.ivStatus.setVisibility(View.VISIBLE);
                    viewHolder.ivStatus.setImageResource(android.R.drawable.ic_media_play);
                    break;
                case FINISHED:
                    viewHolder.ivStatus.setVisibility(View.VISIBLE);
                    viewHolder.ivStatus.setImageResource(R.drawable.ic_check_normal_light);
                    break;
                default:
                    viewHolder.ivStatus.setVisibility(View.INVISIBLE);
            }

            return view;
        }
    }

    private class ViewHolder {
        TextView tvTemperature;
        TextView tvTime;
        ImageView ivStatus;
    }

    private void showEditTemperatureProfileSetpoint(final int idx)
    {
        HmsPickerBuilder hpb = new HmsPickerBuilder()
                .setFragmentManager(getActivity().getSupportFragmentManager())
                .setTimeInMilliseconds(mAdapter.getSetpoint(idx).time)
                .setStyleResId(R.style.BetterPickersDialogFragment);
        hpb.addHmsPickerDialogHandler(new HmsPickerDialogFragment.HmsPickerDialogHandlerV2() {
            @Override
            public void onDialogHmsSet(int reference, boolean isNegative,
                                       int hours, int minutes, int seconds) {
                int time = ((hours * 60) + minutes) * 60 + seconds;
                mAdapter.getSetpoint(idx).time = time * 1000;
                mAdapter.notifyDataSetChanged();
                Log.i(TAG, "" + time);
            }
        });
        hpb.show();

        NumberPickerBuilder nbp = new NumberPickerBuilder()
                .setFragmentManager(getActivity().getSupportFragmentManager())
                .setStyleResId(R.style.BetterPickersDialogFragment)
                .setLabelText("Temperature.");
        if(mAdapter.getSetpoint(idx).temperature != 0.0f)
            nbp.setCurrentNumber(BigDecimal.valueOf(mAdapter.getSetpoint(idx).temperature));
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

    private void showDeleteTemperatureProfileSetpoint(final int idx)
    {
        AlertDialog.Builder adb = new AlertDialog.Builder(getActivity());
        adb.setTitle("Delete?");
        adb.setMessage("Are you sure you want to delete this item?");
        adb.setNegativeButton("No", null);
        adb.setPositiveButton("Yes", new AlertDialog.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                stopAutomaticTemperatureProfileControl();
                TemperatureProfileData.setpoints.remove(idx);
                mAdapter.notifyDataSetChanged();
            }
        });
        adb.show();
    }
}
