package com.example.r15fixture;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

/** Many small Services; each binder answers with its own class name so a client detects a wrong binder. */
public final class ProbeServices {
    public static final int MAIN = 40;
    public static final int REMOTE = 8;

    public abstract static class ProbeService extends Service {
        @Override public IBinder onBind(Intent intent) {
            Binder binder = new Binder();
            binder.attachInterface(null, getClass().getName());
            return binder;
        }
    }

    public static final class S0 extends ProbeService {}
    public static final class S1 extends ProbeService {}
    public static final class S2 extends ProbeService {}
    public static final class S3 extends ProbeService {}
    public static final class S4 extends ProbeService {}
    public static final class S5 extends ProbeService {}
    public static final class S6 extends ProbeService {}
    public static final class S7 extends ProbeService {}
    public static final class S8 extends ProbeService {}
    public static final class S9 extends ProbeService {}
    public static final class S10 extends ProbeService {}
    public static final class S11 extends ProbeService {}
    public static final class S12 extends ProbeService {}
    public static final class S13 extends ProbeService {}
    public static final class S14 extends ProbeService {}
    public static final class S15 extends ProbeService {}
    public static final class S16 extends ProbeService {}
    public static final class S17 extends ProbeService {}
    public static final class S18 extends ProbeService {}
    public static final class S19 extends ProbeService {}
    public static final class S20 extends ProbeService {}
    public static final class S21 extends ProbeService {}
    public static final class S22 extends ProbeService {}
    public static final class S23 extends ProbeService {}
    public static final class S24 extends ProbeService {}
    public static final class S25 extends ProbeService {}
    public static final class S26 extends ProbeService {}
    public static final class S27 extends ProbeService {}
    public static final class S28 extends ProbeService {}
    public static final class S29 extends ProbeService {}
    public static final class S30 extends ProbeService {}
    public static final class S31 extends ProbeService {}
    public static final class S32 extends ProbeService {}
    public static final class S33 extends ProbeService {}
    public static final class S34 extends ProbeService {}
    public static final class S35 extends ProbeService {}
    public static final class S36 extends ProbeService {}
    public static final class S37 extends ProbeService {}
    public static final class S38 extends ProbeService {}
    public static final class S39 extends ProbeService {}
    public static final class R0 extends ProbeService {}
    public static final class R1 extends ProbeService {}
    public static final class R2 extends ProbeService {}
    public static final class R3 extends ProbeService {}
    public static final class R4 extends ProbeService {}
    public static final class R5 extends ProbeService {}
    public static final class R6 extends ProbeService {}
    public static final class R7 extends ProbeService {}
}
