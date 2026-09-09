package com.masteralanlab.emailbox.update

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masteralanlab.emailbox.data.Prefs
import com.masteralanlab.emailbox.data.remote.UpdateInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class UpdatePhase { Idle, Checking, Downloading, Verifying, Done, Failed }

data class UpdateUiState(
    val phase: UpdatePhase = UpdatePhase.Idle,
    val checking: Boolean = false,
    val info: UpdateInfo? = null,
    val progress: Float = 0f,
    val error: String? = null,
    val apk: File? = null,
    /** 用户主动点「检查更新」时为 true，静默检查失败不弹错误。 */
    val manual: Boolean = false,
)

class UpdateViewModel : ViewModel() {

    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private var job: Job? = null

    /** 启动时静默检查；每次冷启动都按当前 versionCode 检查。 */
    fun checkOnStart() {
        // 个人应用：每次冷启动都检查。一次清单 GET 的代价可以忽略，
        // 6 小时节流换来的是「发了新版本 App 却不吭声」。
        check(manual = false)
    }

    fun check(manual: Boolean = true) {
        if (_state.value.phase == UpdatePhase.Downloading) return
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(checking = true, error = null, manual = manual, phase = UpdatePhase.Checking) }
            val info = runCatching { UpdateManager.check() }.getOrNull()
            Prefs.lastUpdateCheck = System.currentTimeMillis()
            if (info == null) {
                _state.update {
                    it.copy(
                        checking = false,
                        phase = UpdatePhase.Idle,
                        info = null,
                        error = if (manual) "当前已是最新版本" else null,
                        manual = manual,
                    )
                }
                if (manual) {
                    delay(1600)
                    _state.update { it.copy(error = null) }
                }
            } else {
                _state.update {
                    it.copy(checking = false, phase = UpdatePhase.Idle, info = info, manual = manual)
                }
            }
        }
    }

    fun dismiss(info: UpdateInfo) {
        _state.update { it.copy(info = null, error = null, phase = UpdatePhase.Idle) }
    }

    fun resetError() = _state.update { it.copy(error = null) }

    fun clear() = _state.update { UpdateUiState() }

    fun downloadAndInstall(context: Context) {
        val info = _state.value.info ?: return
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(phase = UpdatePhase.Downloading, progress = 0f, error = null) }
            runCatching {
                val file = UpdateManager.download(context, info) { p ->
                    _state.update { it.copy(progress = p) }
                }
                _state.update { it.copy(phase = UpdatePhase.Verifying, apk = file) }
                UpdateNotifications.notifyDownloaded(context, file, info.versionName)
                file
            }.onSuccess { file ->
                _state.update { it.copy(phase = UpdatePhase.Done, apk = file) }
            }.onFailure { e ->
                _state.update {
                    it.copy(phase = UpdatePhase.Failed, error = e.message ?: "更新失败")
                }
            }
        }
    }

    fun launchInstall(context: Context): Boolean {
        val file = _state.value.apk ?: return false
        return runCatching {
            context.startActivity(UpdateManager.installIntent(context, file))
            true
        }.getOrDefault(false)
    }

}
