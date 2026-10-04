package com.example.appsandbox.m12

import android.app.job.JobParameters
import android.app.job.JobService
import android.util.Log

/** Physical entry point installed in the Host package for all virtual jobs. */
class JobStubService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        val extras = params.extras
        val instance = extras.getString("m12_instance")
        val guest = extras.getString("m12_guest_package")
        val guestId = extras.getInt("m12_guest_job_id", params.jobId)
        val record = M12RuntimeRegistries.jobs.snapshot().firstOrNull { it.hostJobId == params.jobId }
        if (record == null || record.key.instanceId != instance || record.key.packageName != guest || record.key.guestJobId != guestId || !record.active) {
            Log.w("AppSandbox.M12.Job", "STALE_GENERATION_REJECT hostJobId=${params.jobId} instance=$instance guestJobId=$guestId")
            jobFinished(params, false)
            return false
        }
        Log.i("AppSandbox.M12.Job", "STUB_JOB_START hostJobId=${params.jobId} instance=$instance guest=$guest guestJobId=$guestId " +
            "generation=${record.generation} event=VIRTUAL_JOB_RESOLVE")
        // The coordinator/process-agent boundary is the next dispatch point. Returning false is
        // deliberate until a real Guest JobService instance is attached; the Host job is completed.
        jobFinished(params, false)
        Log.i("AppSandbox.M12.Job", "HOST_JOB_COMPLETE hostJobId=${params.jobId} instance=$instance result=PASS")
        return false
    }

    override fun onStopJob(params: JobParameters): Boolean {
        Log.i("AppSandbox.M12.Job", "GUEST_JOB_STOP hostJobId=${params.jobId}")
        return false
    }
}
