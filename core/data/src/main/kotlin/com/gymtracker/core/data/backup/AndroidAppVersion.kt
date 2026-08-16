package com.gymtracker.core.data.backup

import android.content.Context
import com.gymtracker.core.domain.backup.AppVersion
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** [AppVersion] reading the app's own `versionName` out of `PackageManager`. */
class AndroidAppVersion
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : AppVersion {
        override fun name(): String =
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
    }
