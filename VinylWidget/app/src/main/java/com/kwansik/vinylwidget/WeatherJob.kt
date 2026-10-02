package com.kwansik.vinylwidget

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context

/** 30분마다 날씨 갱신. 재부팅 후에는 런처가 위젯을 갱신할 때(onUpdate) 다시 예약됨 */
class WeatherJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        WeatherFetcher.fetchAsync(this) { jobFinished(params, false) }
        return true
    }

    override fun onStopJob(params: JobParameters) = true
}

object WeatherScheduler {
    private const val JOB_ID = 1001

    fun schedule(ctx: Context) {
        try {
            scheduleInternal(ctx)
        } catch (e: Exception) {
            android.util.Log.e("VinylWidget", "job schedule failed", e)
        }
    }

    private fun scheduleInternal(ctx: Context) {
        val js = ctx.getSystemService(JobScheduler::class.java)
        if (js.getPendingJob(JOB_ID) != null) return
        val job = JobInfo.Builder(JOB_ID, ComponentName(ctx, WeatherJob::class.java))
            .setPeriodic(30 * 60 * 1000L, 10 * 60 * 1000L)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .build()
        js.schedule(job)
    }

    fun cancel(ctx: Context) {
        ctx.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
    }
}
