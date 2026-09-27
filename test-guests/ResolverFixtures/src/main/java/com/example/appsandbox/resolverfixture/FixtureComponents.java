package com.example.appsandbox.resolverfixture;

import android.app.Activity;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.IBinder;

public final class FixtureComponents {
    private FixtureComponents() {}

    public static class ExportedActivity extends Activity {}
    public static class PrivateActivity extends Activity {}
    public static class ChangedActivity extends Activity {}
    public static class EnabledService extends Service {
        @Override public IBinder onBind(Intent intent) { return null; }
    }
    public static class DisabledService extends Service {
        @Override public IBinder onBind(Intent intent) { return null; }
    }
    public static class ProtectedService extends Service {
        @Override public IBinder onBind(Intent intent) { return null; }
    }
    public static class FixtureReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context context, Intent intent) {}
    }
    public static class ChangedReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context context, Intent intent) {}
    }
    public static class FixtureProvider extends ContentProvider {
        @Override public boolean onCreate() { return true; }
        @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
        @Override public String getType(Uri uri) { return null; }
        @Override public Uri insert(Uri uri, ContentValues values) { return null; }
        @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
        @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
    }
}
