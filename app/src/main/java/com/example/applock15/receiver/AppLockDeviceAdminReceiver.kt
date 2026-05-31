package com.locker.app.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class AppLockDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) = Unit
    override fun onDisabled(context: Context, intent: Intent) = Unit
}
