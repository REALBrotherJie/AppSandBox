package com.example.r15fixture;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.InetSocketAddress;
import java.net.Socket;

public final class ProbeActivity extends Activity {
    private static final String TAG = "R15Fixture";

    private static native String nativeMarker();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView view = new TextView(this);
        view.setText("R15 fixture " + getPackageName());
        setContentView(view);
        probeClassLoading();
        probeNative();
        new Thread(this::probeNetwork, "r15-network").start();
    }

    private void probeClassLoading() {
        String[] names = {
                "android.app.Activity", "java.lang.String", "javax.inject.Provider",
                "android.support.v4.os.ResultReceiver", "android.support.v4.content.FileProvider",
                "android.view.OdViewStub"
        };
        ClassLoader guest = getClassLoader();
        ClassLoader platform = Activity.class.getClassLoader();
        for (String name : names) {
            try {
                Class<?> type = Class.forName(name, false, guest);
                ClassLoader owner = type.getClassLoader();
                String source = owner == null || owner == platform ? "PLATFORM" : owner == guest ? "GUEST" : "HOST";
                Log.i(TAG, "R15_CLASS name=" + name + " source=" + source + " owner=" + owner);
            } catch (Throwable error) {
                Log.i(TAG, "R15_CLASS name=" + name + " source=FAIL error=" + error);
            }
        }
    }

    private void probeNative() {
        ApplicationInfo info = getApplicationInfo();
        String dir = info.nativeLibraryDir;
        boolean single = dir != null && !dir.contains(File.pathSeparator);
        boolean present = dir != null && new File(dir, "libr15native.so").isFile();
        Log.i(TAG, "R15_NATIVE_DIR dir=" + dir + " single=" + single + " libPresent=" + present
                + " splits=" + (info.splitSourceDirs == null ? 0 : info.splitSourceDirs.length));
        try {
            System.loadLibrary("r15native");
            Log.i(TAG, "R15_NATIVE_LOAD result=PASS marker=" + nativeMarker() + " mapped=" + mappedPath("libr15native.so"));
        } catch (Throwable error) {
            Log.i(TAG, "R15_NATIVE_LOAD result=FAIL error=" + error);
        }
    }

    private void probeNetwork() {
        try {
            ConnectivityManager manager = getSystemService(ConnectivityManager.class);
            Log.i(TAG, "R15_NETWORK_STATE result=ALLOWED active=" + manager.getActiveNetwork());
        } catch (SecurityException error) {
            Log.i(TAG, "R15_NETWORK_STATE result=DENIED error=" + error.getMessage());
        } catch (Throwable error) {
            Log.i(TAG, "R15_NETWORK_STATE result=ERROR error=" + error);
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("223.5.5.5", 53), 5000);
            Log.i(TAG, "R15_INTERNET result=ALLOWED");
        } catch (SecurityException error) {
            Log.i(TAG, "R15_INTERNET result=DENIED error=" + error.getMessage());
        } catch (Throwable error) {
            String text = String.valueOf(error);
            Log.i(TAG, "R15_INTERNET result=" + (text.contains("EACCES") || text.contains("EPERM") ? "DENIED" : "ERROR") + " error=" + text);
        }
    }

    private static String mappedPath(String library) {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/maps"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(library)) return line.substring(line.indexOf('/'));
            }
        } catch (Exception ignored) {
        }
        return "NONE";
    }
}
