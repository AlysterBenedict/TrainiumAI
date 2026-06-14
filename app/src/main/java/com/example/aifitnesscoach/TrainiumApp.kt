package com.example.aifitnesscoach

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.example.aifitnesscoach.network.FirebaseSyncHelper

class TrainiumApp : Application() {

    private var startedActivitiesCount = 0

    override fun onCreate() {
        super.onCreate()

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

            override fun onActivityStarted(activity: Activity) {
                startedActivitiesCount++
            }

            override fun onActivityResumed(activity: Activity) {}

            override fun onActivityPaused(activity: Activity) {}

            override fun onActivityStopped(activity: Activity) {
                startedActivitiesCount--
                if (startedActivitiesCount == 0) {
                    // Trigger database sync & push to Firestore globally from any screen
                    FirebaseSyncHelper.syncSharedPreferencesToDatabase(activity)
                    FirebaseSyncHelper.pushLocalDataToFirebase(activity)
                }
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
