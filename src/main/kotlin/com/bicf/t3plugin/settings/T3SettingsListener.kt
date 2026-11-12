package com.bicf.t3plugin.settings

import com.intellij.util.messages.Topic

/** Receives a message when the user applies new T3 settings. */
fun interface T3SettingsListener {

    fun settingsChanged()

    companion object {
        @JvmField
        @Topic.AppLevel
        val TOPIC: Topic<T3SettingsListener> = Topic(T3SettingsListener::class.java, Topic.BroadcastDirection.NONE)
    }
}
