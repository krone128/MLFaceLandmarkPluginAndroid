package com.test.mlfacelandmarkplugin

import android.app.Activity
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.contract.ActivityResultContracts

class PermissionHelper {
    companion object {
        fun requestPermission(activity: Activity, permissions: Array<String>, onResult: (Boolean) -> Unit)
        {
            if(permissions.isEmpty()) {
                onResult(true)
                return
            }

            if (activity is ActivityResultCaller) requestPermissionAndroidX(activity, permissions, onResult)
            else PermissionHelperDeprecated.requestPermission(activity, permissions, onResult)
        }

        fun requestPermissionAndroidX(activity: ActivityResultCaller, permissions: Array<String>, onResult: (Boolean) -> Unit)
        {
            activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions())
            { permissionResults ->
                onResult.invoke(
                    permissionResults?.all { it.value } ?: false)
            }.launch(permissions)
        }
    }
}
