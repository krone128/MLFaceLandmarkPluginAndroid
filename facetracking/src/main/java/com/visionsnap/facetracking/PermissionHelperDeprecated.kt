package com.visionsnap.facetracking

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import kotlinx.parcelize.Parcelize

class PermissionHelperDeprecated : android.app.Fragment() {
    @Parcelize
    private data class PermissionResultCallback(val onResult: (Boolean) -> Unit) : android.os.Parcelable

    companion object {
        private const val PCODE = 12321

        fun requestPermission(activity: Activity, permissions: Array<String>, onResult: (Boolean) -> Unit)
        {
            if(permissions.isEmpty()) {
                onResult(true)
                return
            }

            val frag = PermissionHelperDeprecated().apply {
                arguments = Bundle().apply {
                    putStringArray("permissions", permissions)
                    putParcelable("callback", PermissionResultCallback(onResult))
                }
            }

            activity.fragmentManager.beginTransaction()
                .add(0, frag)
                .commitNowAllowingStateLoss()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onAttach(activity: Activity?) {
        super.onAttach(activity)
        checkPermissions()
    }

    private fun checkPermissions() {
        val permissionArray = arguments.getStringArray("permissions");
        val callback = arguments.getParcelable<PermissionResultCallback>("callback")

        if(permissionArray.isNullOrEmpty()
            || permissionArray.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) {
            callback?.onResult?.invoke(true)
            finish()
            return
        }

        requestPermissions(permissionArray, PCODE)
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != PCODE) return

        val callback = arguments.getParcelable<PermissionResultCallback>("callback")
        val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        callback?.onResult?.invoke(allGranted)
        finish()
    }

    private fun finish()
    {
        fragmentManager.beginTransaction()
            .remove(this)
            .commitNowAllowingStateLoss()
    }
}
