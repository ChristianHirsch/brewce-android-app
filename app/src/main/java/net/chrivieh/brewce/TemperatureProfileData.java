package net.chrivieh.brewce;

import java.util.ArrayList;

public class TemperatureProfileData {

    public static class Setpoint {
        public float temperature;
        public long time;
        public enum Status {
            WAITING,
            ACTIVE,
            FINISHED
        }
        public Status status = Status.WAITING;
    }

    public static ArrayList<Setpoint> setpoints = new ArrayList<Setpoint>();

    public static Setpoint getSetpointOfIdx(int idx) {
        return setpoints.get(idx);
    }

    public static long getTimeOfIdx(int idx) {
        return setpoints.get(idx).time;
    }

    public static float getTemperatureOfIdx(int idx) {
        return setpoints.get(idx).temperature;
    }

    public static int size() {
        return setpoints.size();
    }
}
