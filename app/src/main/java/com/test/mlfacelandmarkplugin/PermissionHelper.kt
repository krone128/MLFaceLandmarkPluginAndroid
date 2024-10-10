package com.test.mlfacelandmarkplugin

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

class PermissionHelper : Activity() {
    private val pCode = 12321

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    private fun checkPermissions() {
        var flag = false
        for (s in permissionArray) if (checkSelfPermission(s) != PackageManager.PERMISSION_GRANTED) flag =
            true
        if (flag) {
            requestPermissions(permissionArray, pCode)
        } else {
            finish()
            onResultCallback.invoke(true)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == pCode) {
            var flag = true
            var i = 0
            val len = permissions.size
            while (i < len) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) flag = false
                i++
            }
            onResultCallback.invoke(flag)
            finish()
        }
    }

    companion object {
        private lateinit var onResultCallback: (Boolean) -> Unit
        private lateinit var permissionArray: Array<String>
        fun requestPermission(context: Context, permissions: Array<String>, onResult: (Boolean) -> Unit)
        {
            onResultCallback = onResult
            permissionArray = permissions

            val intent = Intent(context, PermissionHelper::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
