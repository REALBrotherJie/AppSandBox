package com.example.r15fixture;

import android.app.Activity;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.util.Log;
import android.widget.TextView;

/** Ordinary Guest entry point: it knows nothing about AppSandbox and only uses Android JobScheduler. */
public final class JobProbeActivity extends Activity {
    private static final String TAG = "JobProbeFixture";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        String mode = getIntent().getStringExtra("mode");
        int jobId = getIntent().getIntExtra("jobId", 701);
        long delayMs = getIntent().getLongExtra("delayMs", 1L);
        Log.i(TAG, "R1_GUEST_FIXTURE_ENTERED jobId=" + jobId + " mode=" + mode);
        JobScheduler scheduler = getSystemService(JobScheduler.class);
        PersistableBundle extras = new PersistableBundle();
        extras.putString("mode", mode == null ? "false" : mode);
        JobInfo info = new JobInfo.Builder(jobId, new ComponentName(this, JobProbeService.class))
            .setExtras(extras).setMinimumLatency(delayMs).build();
        Log.i(TAG, "R2_ORDINARY_JOB_API_CALL jobId=" + jobId + " scheduler=" + scheduler.getClass().getName());
        int result = scheduler.schedule(info);
        Log.i(TAG, "R4_GUEST_SCHEDULE_RESULT jobId=" + jobId + " result=" + result);
        TextView view = new TextView(this);
        view.setText("Job probe " + jobId + " result=" + result);
        setContentView(view);
    }
}
