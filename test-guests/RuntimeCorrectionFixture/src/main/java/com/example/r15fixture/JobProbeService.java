package com.example.r15fixture;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.PersistableBundle;

/** Zero-adaptation JobService used to observe the real JobServiceEngine boundary. */
public final class JobProbeService extends JobService {
    @Override public boolean onStartJob(JobParameters params) {
        PersistableBundle extras = params.getExtras();
        String mode = extras == null ? "false" : extras.getString("mode", "false");
        getSharedPreferences("job-evidence", MODE_PRIVATE).edit()
            .putString("start", params.getJobId() + ":" + mode).apply();
        if ("false".equals(mode)) return false;
        boolean reschedule = "reschedule".equals(mode);
        new Thread(() -> {
            try { Thread.sleep(250); } catch (InterruptedException ignored) { }
            getSharedPreferences("job-evidence", MODE_PRIVATE).edit()
                .putString("finished", params.getJobId() + ":" + reschedule).apply();
            jobFinished(params, reschedule);
        }, "job-probe-finish").start();
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) {
        getSharedPreferences("job-evidence", MODE_PRIVATE).edit()
            .putString("stop", Integer.toString(params.getJobId())).apply();
        return false;
    }
}
