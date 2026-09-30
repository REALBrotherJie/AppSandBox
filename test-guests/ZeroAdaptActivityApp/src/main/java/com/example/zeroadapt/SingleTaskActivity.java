package com.example.zeroadapt;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class SingleTaskActivity extends Activity {
    private TextView status;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(40, 80, 40, 40);
        status = new TextView(this); status.setText("singleTask create sequence=" + getIntent().getIntExtra("sequence", -1)); root.addView(status);
        Button third = new Button(this); third.setText("Open third from singleTask"); third.setOnClickListener(v -> startActivity(new Intent(this, ThirdActivity.class))); root.addView(third);
        Button again = new Button(this); again.setText("Start singleTask again"); again.setOnClickListener(v -> startActivity(new Intent(this, SingleTaskActivity.class).putExtra("sequence", 2))); root.addView(again);
        setContentView(root); Log.i("ZeroAdapt", "SINGLE_TASK_CREATE");
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); status.setText("singleTask newIntent sequence=" + intent.getIntExtra("sequence", -1)); Log.i("ZeroAdapt", "SINGLE_TASK_NEW_INTENT"); }
}
