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

    List<HashMap<String, Float>> mTempProfilePoints = new ArrayList<HashMap<String, Float>>();
    SimpleAdapter mAdapter;

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
        String[] from = {"setpoint", "time"};
        int[] to = { R.id.temperature_setpoint, R.id.temperature_time };
        mAdapter = new SimpleAdapter(getActivity(),
                mTempProfilePoints,
                R.layout.listitem_temperature,
                from, to);
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

                HashMap<String, Float> value = new HashMap<String, Float>();
                mTempProfilePoints.add(value);
                final int idx = mTempProfilePoints.indexOf(value);

                HmsPickerBuilder hpb = new HmsPickerBuilder()
                        .setFragmentManager(getActivity().getSupportFragmentManager())
                        .setStyleResId(R.style.BetterPickersDialogFragment);
                hpb.addHmsPickerDialogHandler(new HmsPickerDialogFragment.HmsPickerDialogHandlerV2() {
                    @Override
                    public void onDialogHmsSet(int reference, boolean isNegative,
                                               int hours, int minutes, int seconds) {
                        int time = ((hours * 60) + minutes) * 60 + seconds;
                        mTempProfilePoints.get(idx).put("time", (float) time);
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
                        mTempProfilePoints.get(idx).put("setpoint", (float) temp);
                        Log.i(TAG, "" + temp);
                    }
                });
                nbp.show();
            }
        });
    }
}
