package com.example.zeroadapt;

import android.app.Application;
import android.util.Log;

public final class ZeroAdaptApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        Log.i("ZeroAdapt", "Application.onCreate class=" + getClass().getName()
                + " package=" + getPackageName() + " dataDir=" + getApplicationInfo().dataDir
                + " loader=" + getClassLoader());
    }
}
