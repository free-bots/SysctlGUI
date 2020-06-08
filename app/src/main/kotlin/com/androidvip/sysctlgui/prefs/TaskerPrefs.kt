package com.androidvip.sysctlgui.prefs

import android.content.Context
import com.androidvip.sysctlgui.KernelParameter
import com.androidvip.sysctlgui.prefs.base.BasePrefs

class TaskerPrefs(context: Context?, listNumber: Int) :
    BasePrefs(context, fileName = "tasker-params-$listNumber.json") {

    fun isTaskerParam(param: KernelParameter): Boolean {
        return paramExists(param, getUserParamsSet())
    }

    override fun changeListener(): ChangeListener? = null

}
