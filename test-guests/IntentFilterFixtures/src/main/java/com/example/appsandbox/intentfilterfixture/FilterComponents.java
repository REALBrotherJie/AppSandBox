package com.example.appsandbox.intentfilterfixture;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class FilterComponents {
    private FilterComponents() {}

    public static class FilterActivity extends Activity {}
    public static class ExactActivity extends Activity {}
    public static class DisabledActivity extends Activity {}
    public static class ProtectedActivity extends Activity {}
    public static class RevisionV1Activity extends Activity {}
    public static class RevisionV2Activity extends Activity {}

    public static class FilterReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context context, Intent intent) {}
    }
}
