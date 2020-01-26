package com.androidvip.sysctlgui

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*
import java.lang.reflect.Type

class KernelParamUtils(val context: Context) {
    private val prefs: SharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(context) }

    fun exportParamsToUri(uri: Uri): Boolean {

        return try {
            context.contentResolver.openFileDescriptor(uri, "w")?.use {
                FileOutputStream(it.fileDescriptor).use {
                        fileOutputStream: FileOutputStream ->
                    fileOutputStream.write(Gson().toJson(Prefs.getUserParamsSet(context)).toByteArray())
                }
            }
            true
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
            false
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    fun paramsFromUri(uri: Uri): MutableList<KernelParameter> {
        val stringBuilder = StringBuilder()
        context.contentResolver.openInputStream(uri).use { inputStream: InputStream? ->
            BufferedReader(InputStreamReader(inputStream)).use { bufferedReader: BufferedReader ->
                var content: String? = bufferedReader.readLine()
                while (content != null) {
                    stringBuilder.append(content)
                    content = bufferedReader.readLine()
                }
            }
        }

        val type: Type = object : TypeToken<List<KernelParameter>>() {}.type
        return Gson().fromJson(stringBuilder.toString(), type)
    }

    suspend fun commitChanges(kernelParam: KernelParameter) = withContext(Dispatchers.Default) {
        val commandPrefix = if (prefs.getBoolean(Prefs.USE_BUSYBOX, false)) "busybox " else ""
        val command = when (prefs.getString(Prefs.COMMIT_MODE, "sysctl")) {
            "sysctl" -> "${commandPrefix}sysctl -w ${kernelParam.name}=${kernelParam.value}"
            "echo" -> "echo '${kernelParam.value}' > ${kernelParam.path}"
            else -> "busybox sysctl -w ${kernelParam.name}=${kernelParam.value}"
        }

        RootUtils.executeWithOutput(command, "error")
    }


    fun applyParam(kernelParameter: KernelParameter, kernelParamApply: KernelParamApply, customApply: Boolean) {
        val newValue = kernelParameter.value
        val commitMode = prefs.getString(Prefs.COMMIT_MODE, "sysctl")

        if (!prefs.getBoolean(Prefs.ALLOW_BLANK, false) && newValue.isEmpty()) {
            kernelParamApply.onEmptyValue()
        } else {

            GlobalScope.launch(Dispatchers.IO) {
                if (customApply) {
                    kernelParamApply.onCustomApply(kernelParameter)
                } else {
                    val result = commitChanges(kernelParameter)
                    var success = true
                    val feedback = if (commitMode == "sysctl") {
                        if (result == "error" || !result.contains(kernelParameter.name)) {
                            success = false
                            context.getString(R.string.failed)
                        } else {
                            result
                        }
                    } else {
                        if (result == "error") {
                            success = false
                            context.getString(R.string.failed)
                        } else {
                            context.getString(R.string.done)
                        }
                    }

                    if (success) {
                        Prefs.putParam(kernelParameter, context)
                        kernelParamApply.onSuccess()
                    }

                    kernelParamApply.onFeedBack(feedback)
                }
            }
        }
    }

    interface KernelParamApply {
        fun onEmptyValue()
        fun onFeedBack(feedback: String)
        fun onCustomApply(kernelParam: KernelParameter)
        fun onSuccess()
    }
}