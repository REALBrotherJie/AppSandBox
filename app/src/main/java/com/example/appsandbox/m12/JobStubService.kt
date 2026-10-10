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
        val registered = M12RuntimeRegistries.jobs.snapshot().firstOrNull { it.hostJobId == params.jobId }
        val service = extras.getString("m12_guest_service")
        val generation = extras.getLong("m12_generation", 0L)
        val record = registered ?: if (instance != null && guest != null && service != null) {
            VirtualJobRecord(VirtualJobKey("physical-job", guest, instance, guestId), params.jobId, service, generation,
                "$packageName.m12.JobStubService")
        } else null
        if (record == null || record.key.instanceId != instance || record.key.packageName != guest || record.key.guestJobId != guestId || !record.active) {
            Log.w("AppSandbox.M12.Job", "STALE_GENERATION_REJECT hostJobId=${params.jobId} instance=$instance guestJobId=$guestId")
            jobFinished(params, false)
            return false
        }
        Log.i("AppSandbox.M12.Job", "JOB_VIRTUAL_RESOLVE hostJobId=${params.jobId} instance=$instance guest=$guest guestJobId=$guestId " +
            "generation=${record.generation}")
        GuestJobExecutionManager.start(this, params, record) { reschedule -> jobFinished(params, reschedule) }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        return GuestJobExecutionManager.stop(this, params)
    }

    override fun onDestroy() {
        GuestJobExecutionManager.destroy()
        super.onDestroy()
    }
}
