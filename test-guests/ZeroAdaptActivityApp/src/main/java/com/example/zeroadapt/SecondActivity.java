package com.example.zeroadapt;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class SecondActivity extends Activity {
    private TextView status;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = screen("Second Activity");
        status = label(root, extrasSummary(getIntent()));
        button(root, "Open third", v -> startActivity(new Intent(this, ThirdActivity.class)));
        button(root, "Start singleTop again", v -> startActivity(new Intent(this, SecondActivity.class).putExtra("route", "singleTop")));
        button(root, "Back", v -> finish());
        setContentView(root);
        Log.i("ZeroAdapt", "SECOND_CREATE " + status.getText());
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent);
        status.setText(extrasSummary(intent));
        Log.i("ZeroAdapt", "SECOND_NEW_INTENT " + status.getText());
    }
    private String extrasSummary(Intent intent) {
        intent.setExtrasClassLoader(getClassLoader());
        Bundle nested = intent.getBundleExtra("nestedBundle");
        TestPayload parcel = intent.getParcelableExtra("parcel");
        TestSerializable serial = (TestSerializable) intent.getSerializableExtra("serial");
        return "route=" + intent.getStringExtra("route") + " number=" + intent.getIntExtra("number", -1)
                + " enabled=" + intent.getBooleanExtra("enabled", false)
                + " nested=" + (nested == null ? null : nested.getString("nested"))
                + " parcel=" + (parcel == null ? null : parcel.value)
                + " serial=" + (serial == null ? null : serial.value);
    }
    private LinearLayout screen(String title) { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); v.setPadding(40, 80, 40, 40); label(v, title); return v; }
    private TextView label(LinearLayout root, String text) { TextView v = new TextView(this); v.setText(text); root.addView(v); return v; }
    private void button(LinearLayout root, String text, View.OnClickListener listener) { Button v = new Button(this); v.setText(text); v.setOnClickListener(listener); root.addView(v); }
}
