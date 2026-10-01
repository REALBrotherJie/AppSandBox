package com.example.zeroadapt;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public final class LauncherActivity extends Activity {
    private TextView status;
    private void event(String name) {
        Log.i("ZeroAdapt", name + " class=" + getClass().getName()
                + " base=" + getBaseContext().getClass().getName()
                + " app=" + getApplication().getClass().getName()
                + " resources=" + getResources().getClass().getName()
                + " loader=" + getClassLoader());
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        event("Activity.onCreate");
        verifyPackageManager();
        setContentView(R.layout.activity_launcher);
        TextView value = findViewById(R.id.value);
        int count = state == null ? getSharedPreferences("state", MODE_PRIVATE).getInt("count", 0)
                : state.getInt("screenCount", 0);
        value.setText(Integer.toString(count));
        status = findViewById(R.id.status);
        if (state != null) {
            status.setText(state.getString("status", "Restored"));
            Log.i("ZeroAdapt", "STATE_RESTORED count=" + count + " status=" + status.getText());
        }
        Button button = findViewById(R.id.increment);
        button.setOnClickListener(v -> {
            int next = Integer.parseInt(value.getText().toString()) + 1;
            getSharedPreferences("state", MODE_PRIVATE).edit().putInt("count", next).apply();
            value.setText(Integer.toString(next));
            Log.i("ZeroAdapt", "Button.click value=" + next);
        });
        findViewById(R.id.open_explicit).setOnClickListener(v -> startActivity(secondIntent("explicit")));
        findViewById(R.id.open_implicit).setOnClickListener(v -> {
            Intent intent = secondIntent("implicit");
            intent.setComponent(null).setAction("com.example.zeroadapt.IMPLICIT").setPackage(getPackageName());
            startActivity(intent);
        });
        findViewById(R.id.open_result).setOnClickListener(v ->
                startActivityForResult(new Intent(this, ResultActivity.class).putExtra("input", "round-trip"), 42));
        findViewById(R.id.open_single_task).setOnClickListener(v ->
                startActivity(new Intent(this, SingleTaskActivity.class).putExtra("sequence", 1)));
        findViewById(R.id.recreate).setOnClickListener(v -> {
            status.setText("Before recreate");
            recreate();
        });
        getWindow().getDecorView().getViewTreeObserver().addOnPreDrawListener(() -> {
            Log.i("ZeroAdapt", "first-frame window=" + getWindow().getClass().getName()
                    + " decor=" + getWindow().getDecorView().getClass().getName()
                    + " root=" + getWindow().getDecorView().getRootView().getClass().getName());
            return true;
        });
    }

    private Intent secondIntent(String route) {
        Bundle nested = new Bundle();
        nested.putString("nested", "bundle-value");
        return new Intent(this, SecondActivity.class)
                .putExtra("route", route).putExtra("number", 73).putExtra("enabled", true)
                .putExtra("nestedBundle", nested).putExtra("parcel", new TestPayload("parcel-value"))
                .putExtra("serial", new TestSerializable("serial-value"));
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        String value = data == null ? "null" : data.getStringExtra("result");
        status.setText("Result " + requestCode + ": " + value);
        Log.i("ZeroAdapt", "RESULT request=" + requestCode + " code=" + resultCode + " value=" + value);
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putInt("screenCount", Integer.parseInt(((TextView) findViewById(R.id.value)).getText().toString()));
        outState.putString("status", status.getText().toString());
        Log.i("ZeroAdapt", "STATE_SAVED status=" + status.getText());
        super.onSaveInstanceState(outState);
    }

    private void verifyPackageManager() {
        PackageManager pm = getPackageManager();
        try {
            PackageInfo pkg = pm.getPackageInfo(getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
            ApplicationInfo app = pm.getApplicationInfo(getPackageName(), 0);
            ActivityInfo activity = pm.getActivityInfo(new ComponentName(this, LauncherActivity.class), 0);
            Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(getPackageName());
            ResolveInfo resolved = pm.resolveActivity(main, 0);
            Intent implicit = new Intent("com.example.zeroadapt.IMPLICIT").setPackage(getPackageName());
            int implicitCount = pm.queryIntentActivities(implicit, 0).size();
            java.util.List<PackageInfo> visiblePackages = pm.getInstalledPackages(0);
            String[] uidPackages = pm.getPackagesForUid(app.uid);
            boolean missingNotFound;
            try {
                pm.getPackageInfo("com.example.virtual.missing", 0);
                missingNotFound = false;
            } catch (PackageManager.NameNotFoundException expected) {
                missingNotFound = true;
            }
            Log.i("ZeroAdapt", "PM_SELF package=" + pkg.packageName + " app=" + app.packageName + "/" + app.uid
                    + " activity=" + activity.packageName + "/" + activity.name + " source=" + app.sourceDir
                    + " resolve=" + (resolved == null ? "null" : resolved.activityInfo.packageName + "/" + resolved.activityInfo.name)
                    + " queryImplicit=" + implicitCount + " uidPackages=" + java.util.Arrays.toString(uidPackages)
                    + " visiblePackages=" + visiblePackages.size() + "/" + visiblePackages.get(0).packageName
                    + " missingNotFound=" + missingNotFound + " signing=" + (pkg.signingInfo != null));
            Log.i("ZeroAdapt", "CONTEXT_DATA activity=" + getDataDir()
                    + " application=" + getApplication().getDataDir()
                    + " pm=" + app.dataDir + " pmDp=" + app.deviceProtectedDataDir);
        } catch (Exception error) {
            Log.e("ZeroAdapt", "PM_SELF failed", error);
            throw new IllegalStateException(error);
        }
    }

    @Override protected void onStart() { super.onStart(); event("Activity.onStart"); }
    @Override protected void onResume() { super.onResume(); event("Activity.onResume"); }
    @Override protected void onPause() { event("Activity.onPause"); super.onPause(); }
    @Override protected void onStop() { event("Activity.onStop"); super.onStop(); }
    @Override protected void onDestroy() { event("Activity.onDestroy"); super.onDestroy(); }
}
