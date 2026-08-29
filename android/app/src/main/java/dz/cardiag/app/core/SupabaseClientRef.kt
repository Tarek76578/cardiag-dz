package dz.cardiag.app.core

import android.content.Context

/**
 * Single source for the application [Context]. The Supabase client is
 * already initialised in [SupabaseClient]; this reference is used by code
 * that cannot rely on a Composable scope (e.g. resolveFailure inside a
 * coroutine) to fetch localised string resources.
 */
object SupabaseClientRef {
    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext ?: context
    }

    val context: Context
        get() = appContext ?: error("SupabaseClientRef.init(context) must be called from Application.onCreate() before any string resource is resolved")
}
