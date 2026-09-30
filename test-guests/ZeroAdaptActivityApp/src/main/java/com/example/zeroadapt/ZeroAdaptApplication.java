package com.example.zeroadapt;

import android.app.Application;
import android.util.Log;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public final class ZeroAdaptApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        SharedPreferences prefs = getSharedPreferences("account", MODE_PRIVATE);
        String value = prefs.getString("value", null);
        if (value == null) {
            value = getFilesDir().getParentFile().getName();
            prefs.edit().putString("value", value).apply();
        }
        try {
            File file = new File(getFilesDir(), "probe.txt");
            if (!file.exists()) try (FileOutputStream out = new FileOutputStream(file)) { out.write(value.getBytes(StandardCharsets.UTF_8)); }
            SQLiteDatabase db = new SQLiteOpenHelper(this, "probe.db", null, 1) {
                public void onCreate(SQLiteDatabase db) { db.execSQL("CREATE TABLE records (value TEXT)"); }
                public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }
            }.getWritableDatabase();
            db.execSQL("INSERT INTO records(value) SELECT ? WHERE NOT EXISTS (SELECT 1 FROM records)", new Object[]{value});
            db.close();
        } catch (Exception error) { Log.e("ZeroAdapt", "DATA_PROBE write failed", error); }
        Log.i("ZeroAdapt", "DATA_PROBE package=" + getPackageName() + " value=" + value
                + " filesDir=" + getFilesDir() + " cacheDir=" + getCacheDir()
                + " codeCacheDir=" + getCodeCacheDir() + " noBackupDir=" + getNoBackupFilesDir()
                + " dataDir=" + getDataDir() + " databasePath=" + getDatabasePath("probe.db")
                + " customDir=" + getDir("custom", MODE_PRIVATE)
                + " dp=" + getApplicationInfo().deviceProtectedDataDir
                + " cp=" + getDataDir()
                + " appInfoData=" + getApplicationInfo().dataDir
                + " deviceProtected=" + isDeviceProtectedStorage());
        Log.i("ZeroAdapt", "Application.onCreate class=" + getClass().getName()
                + " package=" + getPackageName() + " dataDir=" + getApplicationInfo().dataDir
                + " loader=" + getClassLoader());
    }
}
