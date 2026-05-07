package com.mahmoud.attendify.app

import android.content.Context

/**
 * Simple application context provider used by low-level components that need
 * access to a Context before composition is complete. Initialize from
 * Application.onCreate().
 */
object AppContextProvider {

    @Volatile
    private var ctx: Context? = null

    /**
     * Initialize must be called exactly once (Application.onCreate)
     */
    fun initialize(context: Context) {

        if (ctx != null) {
            throw IllegalStateException("AppContextProvider already initialized")
        }

        ctx = context.applicationContext
    }

    /**
     * Provides application context safely
     */
    fun context(): Context {
        return ctx ?: throw IllegalStateException(
            "AppContextProvider not initialized"
        )
    }
}
