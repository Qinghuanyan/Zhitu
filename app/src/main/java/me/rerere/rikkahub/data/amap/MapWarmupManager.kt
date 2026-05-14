package me.rerere.rikkahub.data.amap

import android.content.Context
import android.util.Log
import com.amap.api.location.AMapLocationClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "MapWarmupManager"

class MapWarmupManager(
    private val context: Context,
) {
    private val appContext = context.applicationContext
    private val warmupMutex = Mutex()

    @Volatile
    private var state: State = State.NotStarted

    suspend fun warmup() {
        if (state == State.Ready || state == State.Running) {
            return
        }
        warmupMutex.withLock {
            if (state == State.Ready || state == State.Running) {
                return
            }
            state = State.Running
            runCatching {
                AMapLocationClient.updatePrivacyShow(appContext, true, true)
                AMapLocationClient.updatePrivacyAgree(appContext, true)
                AMapLocationClient(appContext).apply {
                    onDestroy()
                }
            }.onSuccess {
                state = State.Ready
                Log.i(TAG, "warmup complete")
            }.onFailure {
                state = State.Failed
                Log.w(TAG, "warmup failed", it)
            }
        }
    }

    enum class State {
        NotStarted,
        Running,
        Ready,
        Failed,
    }
}
