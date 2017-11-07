package net.chrivieh.brewce;

import java.util.ArrayList;

/**
 * Created by chrivieh on 07.11.2017.
 */

public class TemperatureProfileData {

    public class Setpoint {
        public float temperature;
        public long time;
    }

    public static ArrayList<Setpoint> setpoints = new ArrayList<Setpoint>();
}
