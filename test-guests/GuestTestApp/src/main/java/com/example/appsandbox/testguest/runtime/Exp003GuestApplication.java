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
    private static final Map<String, String> creationResults = new LinkedHashMap<>();

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
        step("1.resources", () -> require(getString(R.string.exp002_string).equals("EXP002_STRING_1d1c4b6a-87e2-4f31-a9d0-3a6b2e7c9f10")));
        step("2.applicationCast", () -> { Exp003GuestApplication app = (Exp003GuestApplication) getApplicationContext(); require(app == this); });
        step("3.files", () -> {
            try (java.io.OutputStream out = openFileOutput("guest-oncreate.txt", MODE_PRIVATE)) { out.write("GUEST_ONCREATE_FILE".getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
            try (java.io.InputStream in = openFileInput("guest-oncreate.txt"); java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream()) {
                byte[] buffer = new byte[256];
                int count;
                while ((count = in.read(buffer)) != -1) bytes.write(buffer, 0, count);
                require(new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8).equals("GUEST_ONCREATE_FILE"));
            }
        });
        step("4.preferences", () -> { require(getSharedPreferences("guest_prefs", MODE_PRIVATE).edit().putString("marker", "GUEST_ONCREATE_PREF").commit()); require(getSharedPreferences("guest_prefs", MODE_PRIVATE).getString("marker", "").equals("GUEST_ONCREATE_PREF")); });
        step("5.database", () -> {
            try (android.database.sqlite.SQLiteOpenHelper helper = database()) {
                android.database.sqlite.SQLiteDatabase db = helper.getWritableDatabase();
                db.execSQL("DELETE FROM marker");
                db.execSQL("INSERT INTO marker VALUES (?)", new Object[]{"GUEST_ONCREATE_DB"});
                try (android.database.Cursor cursor = db.rawQuery("SELECT value FROM marker", null)) { require(cursor.moveToFirst() && cursor.getString(0).equals("GUEST_ONCREATE_DB")); }
            }
        });
        step("6.layout", () -> { android.view.View view = android.view.LayoutInflater.from(this).inflate(R.layout.exp002_test_layout, null); require(((android.widget.TextView) view.findViewById(R.id.exp002_text)).getText().toString().equals(getString(R.string.exp002_string))); });
        step("7.clipboard", () -> { Object service = getSystemService(CLIPBOARD_SERVICE); require(service != null); creationResults.put("clipboardClass", service.getClass().getName()); });
        step("8.lifecycle", () -> registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            public void onActivityCreated(android.app.Activity a, android.os.Bundle b) {}
            public void onActivityStarted(android.app.Activity a) {}
            public void onActivityResumed(android.app.Activity a) {}
            public void onActivityPaused(android.app.Activity a) {}
            public void onActivityStopped(android.app.Activity a) {}
            public void onActivitySaveInstanceState(android.app.Activity a, android.os.Bundle b) {}
            public void onActivityDestroyed(android.app.Activity a) {}
        }));
    }

    private interface CheckedStep { void run() throws Exception; }
    private static void require(boolean condition) { if (!condition) throw new IllegalStateException("Guest assertion failed"); }
    private void step(String name, CheckedStep step) {
        try { step.run(); creationResults.put(name, "PASS"); }
        catch (Throwable error) { creationResults.put(name, error.getClass().getName() + ":" + error.getMessage()); }
    }
    private android.database.sqlite.SQLiteOpenHelper database() {
        return new android.database.sqlite.SQLiteOpenHelper(this, "guest-oncreate.db", null, 1) {
            public void onCreate(android.database.sqlite.SQLiteDatabase db) { db.execSQL("CREATE TABLE marker (value TEXT)"); }
            public void onUpgrade(android.database.sqlite.SQLiteDatabase db, int oldVersion, int newVersion) { throw new UnsupportedOperationException(); }
        };
    }
    public Map<String, String> onCreateResults() { return new LinkedHashMap<>(creationResults); }
    public Map<String, String> persistedResults() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("preferences", getSharedPreferences("guest_prefs", MODE_PRIVATE).getString("marker", "MISSING"));
        try (android.database.sqlite.SQLiteOpenHelper helper = database(); android.database.Cursor cursor = helper.getReadableDatabase().rawQuery("SELECT value FROM marker", null)) {
            result.put("database", cursor.moveToFirst() ? cursor.getString(0) : "MISSING");
        }
        return result;
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
