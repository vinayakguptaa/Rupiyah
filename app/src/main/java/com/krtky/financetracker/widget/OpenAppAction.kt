package com.krtky.financetracker.widget

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.krtky.financetracker.R
import com.krtky.financetracker.ui.MainActivity

/**
 * Opens [MainActivity] from a home-screen widget with a scale/fade transition
 * instead of the default harsh activity cut.
 *
 * Pair with [MainActivity] close overrides so backing out feels matched.
 */
class OpenAppAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val navigateTo = parameters[NavigateToKey]
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_FROM_WIDGET, true)
            if (!navigateTo.isNullOrBlank()) {
                putExtra("navigate_to", navigateTo)
            }
        }

        val options = ActivityOptions.makeCustomAnimation(
            context,
            R.anim.widget_open_enter,
            R.anim.widget_open_exit,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            options.setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
            )
        }
        context.startActivity(intent, options.toBundle())
    }

    companion object {
        val NavigateToKey = ActionParameters.Key<String>("navigate_to")
    }
}
