package net.chrivieh.brewce;

import android.util.Log;

/**
 * Created by chrivieh on 27.09.2017.
 */

public class PIDController {

    private static float mKp = 1.0f;
    private static float mKi = 0.0f;
    private static float mKd = 0.0f;

    private float mCutoffFrequency = -1.0f;

    private float mSetpoint = 0.0f;
    private float mUpperLimit = 1000.0f;
    private float mLowerLimit = -1000.0f;
    private float mWindupLimit = 1000.0f;

    private float mIntegralError = 0.0f;

    private long mLastCycleTime = 0;
    private float[] mError = {0.0f, 0.0f, 0.0f};
    private float[] mFilteredError = {0.0f, 0.0f, 0.0f};
    private float[] mDerivativeError = {0.0f, 0.0f, 0.0f};
    private float[] mFilteredDerivativeError = {0.0f, 0.0f, 0.0f};

    public float calcControlEffort(float state) {

        long cycleTime = (long) System.currentTimeMillis();

        mError[2] = mError[1];
        mError[1] = mError[0];
        mError[0] = mSetpoint - state;

        if(mLastCycleTime == 0) {
            mLastCycleTime = cycleTime;
            return 0.0f;
        }

        float dt = ((float)(cycleTime - mLastCycleTime)) / 1000.0f;
        mLastCycleTime = cycleTime;

        mIntegralError += mError[0] * dt;
        if(mIntegralError > Math.abs(mWindupLimit))
            mIntegralError = Math.abs(mWindupLimit);
        else if(mIntegralError < -Math.abs(mWindupLimit))
            mIntegralError = -Math.abs(mWindupLimit);

        float c = 1.0f;
        if(mCutoffFrequency != -1.0f) {
            float tan_filt = (float)Math.tan((mCutoffFrequency * 6.2832) * dt / 2.0f);

            if(tan_filt <= 0.0f && tan_filt > -0.01f)
                tan_filt = -0.01f;
            else if(tan_filt > 0.0f && tan_filt < 0.01f)
                tan_filt = 0.01f;

            c = 1.0f/tan_filt;
        }

        mFilteredError[2] = mFilteredError[1];
        mFilteredError[1] = mFilteredError[0];
        mFilteredError[0] = (1.0f/(1.0f + c*c + 1.414f*c))
            * (mError[2] + 2.0f*mError[1] + mError[0]
                - (c*c - 1.414f*c + 1.0f) * mFilteredError[2]
                - (-2.0f*c*c + 2.0f) * mFilteredError[1]);

        mDerivativeError[2] = mDerivativeError[1];
        mDerivativeError[1] = mDerivativeError[0];
        mDerivativeError[0] = (mError[0] - mError[1]) / dt;

        mFilteredDerivativeError[2] = mFilteredDerivativeError[1];
        mFilteredDerivativeError[1] = mFilteredDerivativeError[0];
        mFilteredDerivativeError[0] = (1.0f/(1.0f + c*c + 1.414f*c))
             * (mDerivativeError[2] + 2.0f*mDerivativeError[1] + mDerivativeError[0]
                - (c*c - 1.414f*c + 1.0f) * mFilteredDerivativeError[2]
                - (-2.0f*c*c + 2.0f) * mFilteredDerivativeError[1]);

        float controlEffort = (mKp * mFilteredError[0])
                + (mKi * mIntegralError)
                + (mKd * mFilteredDerivativeError[0]);

        if(controlEffort > mUpperLimit)
            return mUpperLimit;
        if(controlEffort < mLowerLimit)
            return mLowerLimit;

        return controlEffort;
    }

    public float calcControlEffort(float state, float setpoint) {
        mSetpoint = setpoint;
        return calcControlEffort(state);
    }

    public static void setKp(float kp) {
        mKp = kp;
    }
    public static float getKp() { return mKp; }

    public static void setKi(float ki) { mKi = ki; }
    public static float getKi() { return mKi; }

    public static void setKd(float kd) {
        mKd = kd;
    }
    public static float getKd() { return mKd; }

    public void setCutoffFrequency(float mCutoffFrequency) {
        this.mCutoffFrequency = mCutoffFrequency;
    }

    public void setSetpoint(float mSetpoint) {
        this.mSetpoint = mSetpoint;
    }

    public void setUpperLimit(float mUpperLimit) {
        this.mUpperLimit = mUpperLimit;
    }

    public void setLowerLimit(float mLowerLimit) {
        this.mLowerLimit = mLowerLimit;
    }

    public void setWindupLimit(float mWindupLimit) {
        this.mWindupLimit = mWindupLimit;
    }

    public float getSetpoint() { return mSetpoint; }
}
