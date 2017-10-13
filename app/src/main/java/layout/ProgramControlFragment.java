package layout;


import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import com.codetroopers.betterpickers.hmspicker.HmsPickerBuilder;
import com.codetroopers.betterpickers.hmspicker.HmsPickerDialogFragment;
import com.codetroopers.betterpickers.numberpicker.NumberPickerBuilder;
import com.codetroopers.betterpickers.numberpicker.NumberPickerDialogFragment;

import net.chrivieh.brewce.MainActivity;
import net.chrivieh.brewce.R;
import net.chrivieh.brewce.TemperatureControlService;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ProgramControlFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ProgramControlFragment extends Fragment {

    public static final String TAG = ProgramControlFragment.class.getSimpleName();

    private FloatingActionButton fab;

    SetpointListAdapter mAdapter;

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
        fab = (FloatingActionButton) getActivity().findViewById(R.id.fab);
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


    private class Setpoint {
        float temperature;
        int time;
    }

    private class SetpointListAdapter extends BaseAdapter {

        private ArrayList<Setpoint> mSetpoints;
        private LayoutInflater mInflator;

        public SetpointListAdapter() {
            super();
            mSetpoints = new ArrayList<Setpoint>();
            mInflator = ProgramControlFragment.this.getActivity().getLayoutInflater();
        }

        public void addSetpoint(Setpoint setpoint) {
            if(!mSetpoints.contains(setpoint))
                mSetpoints.add(setpoint);
        }

        public void addSetpoint(float temperature, int time) {
            Setpoint setpoint = new Setpoint();
            setpoint.temperature = temperature;
            setpoint.time = time;
            if(!mSetpoints.contains(setpoint))
                mSetpoints.add(setpoint);
        }

        public int getSetpointIdx(Setpoint setpoint) {
            return mSetpoints.indexOf(setpoint);
        }

        public Setpoint getSetpoint(int pos) {
            return mSetpoints.get(pos);
        }

        public void clear() {
            mSetpoints.clear();
        }

        @Override
        public int getCount() {
             return mSetpoints.size();
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

            Setpoint setpoint = mSetpoints.get(i);
            viewHolder.temperature.setText(""  + setpoint.temperature);
            viewHolder.time.setText(""  + setpoint.time);

            return view;
        }
    }

    private class ViewHolder {
        TextView temperature;
        TextView time;
    }
}
