package com.example.zeroadapt;

import android.app.Activity;
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

    @Override protected void onStart() { super.onStart(); event("Activity.onStart"); }
    @Override protected void onResume() { super.onResume(); event("Activity.onResume"); }
    @Override protected void onPause() { event("Activity.onPause"); super.onPause(); }
    @Override protected void onStop() { event("Activity.onStop"); super.onStop(); }
    @Override protected void onDestroy() { event("Activity.onDestroy"); super.onDestroy(); }
}
