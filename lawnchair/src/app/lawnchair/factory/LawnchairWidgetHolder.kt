package app.lawnchair.factory

import android.content.Context
import com.android.internal.annotations.Keep
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.ScreenOnTracker
import com.android.launcher3.widget.LauncherWidgetHolder
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

class LawnchairWidgetHolder @AssistedInject constructor(
    @Assisted("UI_CONTEXT") context: Context,
) : LauncherWidgetHolder(context) {

    private val screenOnListener = ScreenOnTracker.ScreenOnListener { isOn ->
        if (!isOn) {
            // Screen turned off — immediately stop listening to save battery
            MAIN_EXECUTOR.execute { forceStopListeningNow() }
        }
    }

    init {
        ScreenOnTracker.INSTANCE[context].addListener(screenOnListener)
    }

    /**
     * Only require FLAG_STATE_IS_NORMAL and FLAG_ACTIVITY_STARTED to start listening.
     * This removes the dependency on FLAG_ACTIVITY_RESUMED (set in onDeferredResumed),
     * allowing widget listening to start immediately in onStart() — saving one frame
     * of delay and enabling parallel startup with model binding.
     */
    override fun shouldListen(flags: Int): Boolean {
        return (flags and (FLAG_STATE_IS_NORMAL or FLAG_ACTIVITY_STARTED)) ==
            (FLAG_STATE_IS_NORMAL or FLAG_ACTIVITY_STARTED)
    }

    override fun destroy() {
        ScreenOnTracker.INSTANCE[mContext].removeListener(screenOnListener)
        super.destroy()
    }

    @Keep
    @AssistedFactory
    interface Factory : WidgetHolderFactory {
        override fun newInstance(@Assisted("UI_CONTEXT") context: Context): LauncherWidgetHolder
    }
}
