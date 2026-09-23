package com.example.appsandbox.testguest.runtime;

import android.app.Application;
import android.os.Build;
import com.example.appsandbox.testguest.R;
import java.util.LinkedHashMap;
import java.util.Map;

public class Exp003GuestApplication extends Application {
    public static int constructorCount;
    public static boolean onCreateCalled;
    public static String attachBaseClass;
    public static String attachBasePackage;

    public Exp003GuestApplication() {
        constructorCount++;
    }

    @Override
    protected void attachBaseContext(android.content.Context base) {
        attachBaseClass = base == null ? "null" : base.getClass().getName();
        attachBasePackage = base == null ? "null" : base.getPackageName();
        super.attachBaseContext(base);
    }

    @Override
    public void onCreate() {
        onCreateCalled = true;
    }

    public Map<String, String> observe() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("packageName", getPackageName());
        values.put("baseContextClass", getBaseContext() == null ? "null" : getBaseContext().getClass().getName());
        values.put("applicationContextClass", getApplicationContext() == null ? "null" : getApplicationContext().getClass().getName());
        values.put("applicationContextIsThis", Boolean.toString(getApplicationContext() == this));
        values.put("classLoader", getClassLoader() == null ? "null" : getClassLoader().getClass().getName());
        values.put("guestString", getString(R.string.exp002_string));
        values.put("applicationInfoPackage", getApplicationInfo().packageName);
        values.put("applicationInfoDataDir", getApplicationInfo().dataDir);
        values.put("filesDir", getFilesDir().getAbsolutePath());
        values.put("opPackageName", getOpPackageName());
        if (Build.VERSION.SDK_INT >= 31) {
            values.put("attributionPackage", getAttributionSource().getPackageName());
            values.put("attributionUid", Integer.toString(getAttributionSource().getUid()));
        }
        values.put("processName", Application.getProcessName());
        return values;
    }
}
