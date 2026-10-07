package com.vicechanger.app.ui

/** All destinations in the app. Simple and hand-rolled: the back stack is a list of these. */
sealed class Screen(val route: String, val title: String) {
    data object Home : Screen("home", "VICE CHANGER")
    data object Live : Screen("live", "Live Voice")
    data object Presets : Screen("presets", "Choose Voice")
    data object Custom : Screen("custom", "Custom Girl")
    data object VoiceMessage : Screen("voice_message", "Voice Message")
    data object MobileLegends : Screen("mobile_legends", "Mobile Legends Mode")
    data object Messenger : Screen("messenger", "Messenger Voice Mode")
    data object Settings : Screen("settings", "Settings")

    companion object {
        val ALL: List<Screen> = listOf(
            Home, Live, Presets, Custom, VoiceMessage, MobileLegends, Messenger, Settings,
        )

        fun fromRoute(route: String): Screen = ALL.firstOrNull { it.route == route } ?: Home
    }
}
