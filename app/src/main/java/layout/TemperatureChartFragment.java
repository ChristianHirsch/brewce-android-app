package layout;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import net.chrivieh.brewce.BluetoothLeService;
import net.chrivieh.brewce.MainActivity;
import net.chrivieh.brewce.R;
import net.chrivieh.brewce.TemperatureControlService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link TemperatureChartFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class TemperatureChartFragment extends Fragment {

    public static class TemperatureMeasurement extends Entry {
        public float temperature;
        public long timestamp;

        public TemperatureMeasurement(float temperature, long timestamp) {
            super(timestamp, temperature);
            this.temperature = temperature;
            this.timestamp = timestamp;
        }
    }
    public static List<Entry> temperatureMeasurements = new ArrayList<>();
    public static List<Entry> temperatureTargets = new ArrayList<>();


    private LineChart mLineChart;

    private LineDataSet mTemperatureDataSet;
    private LineDataSet mTemperatureTargetDataSet;

    private LineData mTemperatureData;

    public TemperatureChartFragment() {
        // Required empty public constructor
    }

    public static TemperatureChartFragment newInstance() {
        TemperatureChartFragment fragment = new TemperatureChartFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
        }

        getActivity().registerReceiver(mBroadcastReceiver, makeIntentFilter());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_temperature_chart, container, false);
        mLineChart = (LineChart) view.findViewById(R.id.chart);
        mLineChart.getLegend().setEnabled(false);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        getAndDrawChart();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getActivity().unregisterReceiver(mBroadcastReceiver);
    }

    private void getAndDrawChart() {
        mTemperatureDataSet = new LineDataSet(
                temperatureMeasurements, "Temperature");
        mTemperatureDataSet.setDrawCircles(false);
        mTemperatureDataSet.setDrawValues(false);
        mTemperatureDataSet.setLineWidth(3.0f);

        temperatureTargets.add(
                new TemperatureMeasurement(0.0f, SystemClock.uptimeMillis()));
        mTemperatureTargetDataSet = new LineDataSet(
                temperatureTargets, "Target");
        mTemperatureTargetDataSet.setDrawCircles(false);
        mTemperatureTargetDataSet.setDrawValues(false);
        mTemperatureTargetDataSet.setLineWidth(3.0f);

        mTemperatureData = new LineData();
        mTemperatureData.addDataSet(mTemperatureDataSet);
        mTemperatureData.addDataSet(mTemperatureTargetDataSet);

        mLineChart.setData(mTemperatureData);
        mLineChart.invalidate();
    }

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if(BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                mTemperatureDataSet.notifyDataSetChanged();
                //mTemperatureTargetDataSet.notifyDataSetChanged();

                mTemperatureData.notifyDataChanged();

                mLineChart.notifyDataSetChanged();
                mLineChart.invalidate();
            }
        }
    };

    private IntentFilter makeIntentFilter() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        //intentFilter.addAction(TemperatureControlService.);
        return intentFilter;
    }
}
