package net.chrivieh.brewce;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.support.design.widget.TabLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.codetroopers.betterpickers.numberpicker.NumberPickerBuilder;
import com.codetroopers.betterpickers.numberpicker.NumberPickerDialogFragment;
import com.github.mikephil.charting.data.Entry;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import layout.AutomaticControlFragment;
import layout.ManualControlFragment;
import layout.SettingsFragment;
import layout.TemperatureChartFragment;
import layout.TemperatureProfileControlFragment;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = MainActivity.class.getSimpleName();

    public final static String ACTION_START_SCAN =
            "net.chrivieh.brewce.MainActivity.ACTION_START_SCAN";

    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link android.support.v4.app.FragmentStatePagerAdapter}.
     */
    private SectionsPagerAdapter mSectionsPagerAdapter;

    BluetoothLeService mBluetoothLeService = null;

    private static final int PERMISSION_ENABLE_BT = 1;
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 2;
    private static final int PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE = 3;

    private boolean uiElementsInitialized = false;

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    private ViewPager mViewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        TabLayout tabLayout = (TabLayout) findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(mViewPager);
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if(tab.getPosition() == 2)
                    findViewById(R.id.fab).setVisibility(View.VISIBLE);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                if(tab.getPosition() == 2)
                    findViewById(R.id.fab).setVisibility(View.INVISIBLE);
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        });

        checkForBlePermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        //registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
    }

    @Override
    protected void onPause() {
        super.onPause();
        //unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_manual_control, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_scan) {
            checkForBlePermissions();
            sendStartBleScanBroadcast();
            return true;
        }
        else if (id == R.id.action_settings) {
            showEditPIDValue();
            return true;
        }
        else if (id == R.id.action_save_temperature_measurements) {
            if(isExternalStorageWritable() == false)
                Log.e(TAG, "error writing temperature measurements");
            writeTemperatureMeasurementsToFile();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == PERMISSION_ENABLE_BT) {
            if (resultCode == Activity.RESULT_OK) {
                sendStartBleScanBroadcast();
            }
        }
    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            switch (position)
            {
                case 1:
                    return AutomaticControlFragment.newInstance();
                case 2:
                    return TemperatureProfileControlFragment.newInstance();
                case 3:
                    return layout.TemperatureChartFragment.newInstance();
                default:
                    return ManualControlFragment.newInstance();

            }
        }

        @Override
        public int getCount() {
            // Show 4 total pages.
            return 4;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return "Manual Control";
                case 1:
                    return "Automatic Control";
                case 2:
                    return "Profile Control";
                case 3:
                    return "Temperature Chart";
            }
            return null;
        }
    }

    private void sendStartBleScanBroadcast() {
        Intent intent = new Intent(ACTION_START_SCAN);
        sendBroadcast(intent);
    }

    private void checkForBlePermissions() {
        // check if bluetooth is enabled
        BluetoothAdapter bluetoothAdapter =
                ((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();

        if(bluetoothAdapter == null || bluetoothAdapter.isEnabled() == false) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, PERMISSION_ENABLE_BT);
        }

        // check if location permissions are granted
        if(this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    PERMISSION_REQUEST_COARSE_LOCATION);
        }

        // check if location permissions are granted
        if(this.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE);
        }
    }

    private void showEditPIDValue()
    {
        final float kp = PIDController.getKp();
        final float ki = PIDController.getKi();
        final float kd = PIDController.getKd();

        // kD
        NumberPickerBuilder nbp = new NumberPickerBuilder()
                .setFragmentManager(getSupportFragmentManager())
                .setStyleResId(R.style.BetterPickersDialogFragment)
                .setLabelText("Set kD.");
        if(kp != 0.0f)
            nbp.setCurrentNumber(BigDecimal.valueOf(kd));
        nbp.addNumberPickerDialogHandler(new NumberPickerDialogFragment.NumberPickerDialogHandlerV2() {
            @Override
            public void onDialogNumberSet(int reference, BigInteger number, double decimal,
                                          boolean isNegative, BigDecimal fullNumber) {
                PIDController.setKd(fullNumber.floatValue());
            }
        });
        nbp.show();

        // kI
        nbp = new NumberPickerBuilder()
                .setFragmentManager(getSupportFragmentManager())
                .setStyleResId(R.style.BetterPickersDialogFragment)
                .setLabelText("Set kI.");
        if(kp != 0.0f)
            nbp.setCurrentNumber(BigDecimal.valueOf(ki));
        nbp.addNumberPickerDialogHandler(new NumberPickerDialogFragment.NumberPickerDialogHandlerV2() {
            @Override
            public void onDialogNumberSet(int reference, BigInteger number, double decimal,
                                          boolean isNegative, BigDecimal fullNumber) {
                PIDController.setKi(fullNumber.floatValue());
            }
        });
        nbp.show();

        // kP
        nbp = new NumberPickerBuilder()
                .setFragmentManager(getSupportFragmentManager())
                .setStyleResId(R.style.BetterPickersDialogFragment)
                .setLabelText("Set kP.");
        if(kp != 0.0f)
            nbp.setCurrentNumber(BigDecimal.valueOf(kp));
        nbp.addNumberPickerDialogHandler(new NumberPickerDialogFragment.NumberPickerDialogHandlerV2() {
            @Override
            public void onDialogNumberSet(int reference, BigInteger number, double decimal,
                                          boolean isNegative, BigDecimal fullNumber) {
                PIDController.setKp(fullNumber.floatValue());
            }
        });
        nbp.show();
    }

    /* Checks if external storage is available for read and write */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    private void writeTemperatureMeasurementsToFile() {
        if(isExternalStorageWritable() == false)
            return;

        File file = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS),
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Calendar.getInstance().getTime())
                    + "_brewce_data.csv");
        try {
            file.createNewFile();
            BufferedWriter bw = new BufferedWriter(new FileWriter(file));
            bw.write("\"Time\", \"Temperature\"");
            bw.newLine();
            for(Entry tm :
                    TemperatureChartFragment.temperatureMeasurements) {
                tm.getX();
                bw.write(String.format("%d" +
                        ",%.3f", (int)tm.getX(), tm.getY()));
                bw.newLine();
            }
            bw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
