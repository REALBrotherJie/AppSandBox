package com.example.r15fixture;

import android.app.Activity;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.util.Log;
import android.widget.TextView;

/** Ordinary Guest entry point: it knows nothing about AppSandbox and only uses Android JobScheduler. */
public final class JobProbeActivity extends Activity {
    private static final String TAG = "JobProbeFixture";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        handle(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handle(intent);
    }

    private void handle(Intent intent) {
        String mode = intent.getStringExtra("mode");
        String action = intent.getStringExtra("action");
        int jobId = intent.getIntExtra("jobId", 701);
        long delayMs = intent.getLongExtra("delayMs", 1L);
        Log.i(TAG, "R1_GUEST_FIXTURE_ENTERED jobId=" + jobId + " mode=" + mode);
        JobScheduler scheduler = getSystemService(JobScheduler.class);
        if ("cancel".equals(action)) {
            Log.i(TAG, "R2_ORDINARY_JOB_API_CALL action=cancel jobId=" + jobId);
            scheduler.cancel(jobId);
            return;
        }
        if ("cancelAll".equals(action)) {
            Log.i(TAG, "R2_ORDINARY_JOB_API_CALL action=cancelAll");
            scheduler.cancelAll();
            return;
        }
        PersistableBundle extras = new PersistableBundle();
        extras.putString("mode", mode == null ? "false" : mode);
        JobInfo info = new JobInfo.Builder(jobId, new ComponentName(this, JobProbeService.class))
            .setExtras(extras).setMinimumLatency(delayMs).build();
        Log.i(TAG, "R2_ORDINARY_JOB_API_CALL jobId=" + jobId + " scheduler=" + scheduler.getClass().getName());
        int result = scheduler.schedule(info);
        Log.i(TAG, "R4_GUEST_SCHEDULE_RESULT jobId=" + jobId + " result=" + result);
        if ("scheduleReplace".equals(action)) {
            PersistableBundle replacementExtras = new PersistableBundle();
            replacementExtras.putString("mode", "async");
            JobInfo replacement = new JobInfo.Builder(jobId, new ComponentName(this, JobProbeService.class))
                .setExtras(replacementExtras).setMinimumLatency(delayMs).build();
            int replacementResult = scheduler.schedule(replacement);
            Log.i(TAG, "R2_ORDINARY_JOB_API_CALL action=replace jobId=" + jobId + " result=" + replacementResult);
        } else if ("scheduleCancel".equals(action)) {
            scheduler.cancel(jobId);
            Log.i(TAG, "R2_ORDINARY_JOB_API_CALL action=cancel jobId=" + jobId);
        } else if ("scheduleCancelAll".equals(action)) {
            JobInfo second = new JobInfo.Builder(jobId + 1, new ComponentName(this, JobProbeService.class))
                .setExtras(extras).setMinimumLatency(delayMs).build();
            scheduler.schedule(second);
            scheduler.cancelAll();
            Log.i(TAG, "R2_ORDINARY_JOB_API_CALL action=cancelAll jobIds=" + jobId + "," + (jobId + 1));
        }
        TextView view = new TextView(this);
        view.setText("Job probe " + jobId + " result=" + result);
        setContentView(view);
    }
}
