package com.test.mlfacelandmarkplugin

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import kotlinx.parcelize.Parcelize

class PermissionHelper : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_Translucent)
    }

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

    class PermissionHelperFragment : android.app.Fragment() {
        @Deprecated("Deprecated in Java")
        override fun onAttach(activity: Activity?) {
            super.onAttach(activity)
            checkPermissions()
        }

        private fun checkPermissions() {
            val permissionArray = arguments.getStringArray("permissions");
            val callback = arguments.getParcelable<PermissionResultCallback>("callback")

            if(permissionArray.isNullOrEmpty()) {
                callback?.onResult?.invoke(true)
                finish()
                return
            }

            if(permissionArray.any { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED })
            {
                requestPermissions(permissionArray, pCode)
                return
            }

            callback?.onResult?.invoke(true)
            finish()
        }

        @Deprecated("Deprecated in Java")
        override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String>,
            grantResults: IntArray
        ) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            if (requestCode != pCode) return

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

    @Parcelize
    data class PermissionResultCallback(val onResult: (Boolean) -> Unit) : android.os.Parcelable

    companion object {
        private val pCode = 12321
        private val fragmentTag = "PermissionHelperFragment"
        private lateinit var onResultCallback: (Boolean) -> Unit
        private lateinit var permissionArray: Array<String>
        fun requestPermission(activity: Activity, permissions: Array<String>, onResult: (Boolean) -> Unit)
        {
            requestPermissionFragment(activity, permissions, onResult)
            return

            onResultCallback = onResult
            permissionArray = permissions

            val intent = Intent(activity.applicationContext, PermissionHelper::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.startActivityForResult(intent, pCode)
        }

        fun requestPermissionFragment(activity: Activity, permissions: Array<String>, onResult: (Boolean) -> Unit)
        {
            var frag = PermissionHelperFragment();
            frag.arguments = Bundle().apply {
                putStringArray("permissions", permissions)
                putParcelable("callback", PermissionResultCallback(onResult))
            }
            activity.fragmentManager.beginTransaction()
                .add(0, frag, fragmentTag)
                .commitNowAllowingStateLoss()
        }
    }
}
