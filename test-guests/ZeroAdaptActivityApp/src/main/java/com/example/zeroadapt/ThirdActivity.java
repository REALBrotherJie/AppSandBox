package com.example.zeroadapt;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class ThirdActivity extends Activity {
    private static int createCount;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        createCount++;
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(40, 80, 40, 40);
        TextView label = new TextView(this); label.setText("Third Activity instance=" + createCount); root.addView(label);
        Button standard = new Button(this); standard.setText("Open another third");
        standard.setOnClickListener(v -> startActivity(new Intent(this, ThirdActivity.class)));
        root.addView(standard);
        Button clearTop = new Button(this); clearTop.setText("Clear top to second");
        clearTop.setOnClickListener(v -> startActivity(new Intent(this, SecondActivity.class)
                .putExtra("route", "clearTop").addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)));
        root.addView(clearTop); setContentView(root);
        Log.i("ZeroAdapt", "THIRD_CREATE instance=" + createCount);
    }
}
