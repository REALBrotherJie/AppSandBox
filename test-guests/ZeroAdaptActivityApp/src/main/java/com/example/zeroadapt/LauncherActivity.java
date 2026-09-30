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
        int count = getSharedPreferences("state", MODE_PRIVATE).getInt("count", 0);
        value.setText(Integer.toString(count));
        Button button = findViewById(R.id.increment);
        button.setOnClickListener(v -> {
            int next = Integer.parseInt(value.getText().toString()) + 1;
            getSharedPreferences("state", MODE_PRIVATE).edit().putInt("count", next).apply();
            value.setText(Integer.toString(next));
            Log.i("ZeroAdapt", "Button.click value=" + next);
        });
        getWindow().getDecorView().getViewTreeObserver().addOnPreDrawListener(() -> {
            Log.i("ZeroAdapt", "first-frame window=" + getWindow().getClass().getName()
                    + " decor=" + getWindow().getDecorView().getClass().getName()
                    + " root=" + getWindow().getDecorView().getRootView().getClass().getName());
            return true;
        });
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
