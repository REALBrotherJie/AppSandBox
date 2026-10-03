package com.example.r15fixture;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.IBinder;
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
        probeExternalStorageAndProcess();
        new Thread(this::probeNetwork, "r15-network").start();
        new Thread(this::probeServices, "r17-services").start();
    }

    /**
     * Binds more Services than one stub slot holds, in two waves: every binder must be its own Service's,
     * and the second wave only fits if Services destroyed after the first wave give their stubs back.
     */
    private void probeServices() {
        java.util.List<Class<?>> first = new java.util.ArrayList<>();
        for (int i = 0; i < ProbeServices.MAIN / 2; i++) first.add(serviceClass("S" + i));
        for (int i = 0; i < ProbeServices.REMOTE; i++) first.add(serviceClass("R" + i));
        java.util.List<Class<?>> second = new java.util.ArrayList<>();
        for (int i = ProbeServices.MAIN / 2; i < ProbeServices.MAIN; i++) second.add(serviceClass("S" + i));
        java.util.List<android.content.ServiceConnection> connections = bindWave(1, first);
        for (android.content.ServiceConnection connection : connections) unbindService(connection);
        try { Thread.sleep(3000); } catch (InterruptedException ignored) { }
        java.util.List<android.content.ServiceConnection> wave2 = bindWave(2, second);
        for (android.content.ServiceConnection connection : wave2) unbindService(connection);
    }

    private static Class<?> serviceClass(String name) {
        try {
            return Class.forName(ProbeServices.class.getName() + "$" + name);
        } catch (ClassNotFoundException error) {
            throw new IllegalStateException(error);
        }
    }

    private java.util.List<android.content.ServiceConnection> bindWave(int wave, java.util.List<Class<?>> services) {
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(services.size());
        java.util.Map<String, String> answers = new java.util.concurrent.ConcurrentHashMap<>();
        java.util.List<android.content.ServiceConnection> connections = new java.util.ArrayList<>();
        int refused = 0;
        for (Class<?> service : services) {
            android.content.ServiceConnection connection = new android.content.ServiceConnection() {
                @Override public void onServiceConnected(android.content.ComponentName name, IBinder binder) {
                    String descriptor;
                    try {
                        descriptor = binder.getInterfaceDescriptor();
                    } catch (Throwable error) {
                        descriptor = "ERROR " + error;
                    }
                    answers.put(service.getName(), String.valueOf(descriptor));
                    latch.countDown();
                }
                @Override public void onServiceDisconnected(android.content.ComponentName name) { }
            };
            if (bindService(new android.content.Intent(this, service), connection, BIND_AUTO_CREATE)) {
                connections.add(connection);
            } else {
                refused++;
                latch.countDown();
            }
        }
        try { latch.await(30, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
        int own = 0;
        StringBuilder wrong = new StringBuilder();
        for (Class<?> service : services) {
            String answer = answers.get(service.getName());
            if (service.getName().equals(answer)) own++;
            else wrong.append(' ').append(service.getSimpleName()).append("->").append(answer);
        }
        Log.i(TAG, "R17_SERVICES wave=" + wave + " own=" + own + "/" + services.size() + " refused=" + refused
                + " wrong=[" + wrong.toString().trim() + "]");
        return connections;
    }

    /** App-specific external dirs (framework and self-assembled paths) and the /proc process name. */
    private void probeExternalStorageAndProcess() {
        File files = getExternalFilesDir(null);
        File cache = getExternalCacheDir();
        File assembled = new File(android.os.Environment.getExternalStorageDirectory(),
                "Android/data/" + getPackageName() + "/files/assembled.txt");
        Log.i(TAG, "R16_EXTERNAL files=" + files + " cache=" + cache
                + " filesWrite=" + writeMarker(files == null ? null : new File(files, "framework.txt"))
                + " cacheWrite=" + writeMarker(cache == null ? null : new File(cache, "cache.txt"))
                + " assembledWrite=" + writeMarker(assembled) + " assembledVisible=" + new File(files, "assembled.txt").exists());
        String cmdline = "";
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/cmdline"))) {
            String line = reader.readLine();
            cmdline = line == null ? "" : line.replace("\u0000", "");
        } catch (Throwable error) {
            cmdline = "ERROR " + error;
        }
        Log.i(TAG, "R16_PROCESS cmdline=" + cmdline + " applicationProcess=" + android.app.Application.getProcessName());
    }

    private static String writeMarker(File file) {
        if (file == null) return "NULL_DIR";
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
            out.write("r16".getBytes());
            return "PASS";
        } catch (Throwable error) {
            return "FAIL " + error.getMessage();
        }
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
