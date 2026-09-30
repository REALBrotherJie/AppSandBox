package com.example.zeroadapt;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

public final class ResultActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Button done = new Button(this); done.setText("Return result"); setContentView(done);
        done.setOnClickListener(v -> { setResult(RESULT_OK, new Intent().putExtra("result", "ok:" + getIntent().getStringExtra("input"))); finish(); });
        Log.i("ZeroAdapt", "RESULT_CREATE input=" + getIntent().getStringExtra("input"));
    }
}
