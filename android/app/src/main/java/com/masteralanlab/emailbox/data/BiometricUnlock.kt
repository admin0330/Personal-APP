package com.masteralanlab.emailbox.data

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

data class BiometricAvailability(
    val available: Boolean,
    val reason: String,
)

object BiometricUnlock {

    private val authenticators =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun availability(context: Context): BiometricAvailability {
        val code = runCatching {
            BiometricManager.from(context).canAuthenticate(authenticators)
        }.getOrElse { BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED }
        return when (code) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability(true, "可使用指纹或设备密码解锁")
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                BiometricAvailability(false, "设备没有可用的生物识别硬件")
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                BiometricAvailability(false, "生物识别硬件暂不可用")
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                BiometricAvailability(false, "请先在系统设置中设置指纹或设备密码")
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                BiometricAvailability(false, "系统安全更新后才能使用生物识别")
            else -> BiometricAvailability(false, "当前设备不支持指纹或设备密码解锁")
        }
    }

    fun shouldRequire(context: Context): Boolean =
        Prefs.biometricUnlockEnabled && Prefs.hasSession && availability(context).available

    fun authenticate(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit,
    ) {
        val available = availability(activity)
        if (!available.available) {
            onFailure(available.reason)
            return
        }

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onFailure(errString.toString().ifBlank { "解锁未完成" })
                }
            },
        )
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("解锁 Ym1r")
            .setSubtitle("使用指纹或设备密码继续")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(authenticators)
        } else {
            builder.setDeviceCredentialAllowed(true)
        }
        prompt.authenticate(builder.build())
    }
}
