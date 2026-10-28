package com.sergioalexo.mediamover.model

import com.sergioalexo.mediamover.data.Storage

/** Known places messengers save media. Only the ones that exist on the phone are added to a rule. */
data class Preset(val name: String, val candidates: List<String>)

object Presets {
    val all = listOf(
        Preset(
            "WhatsApp",
            listOf("Android/media/com.whatsapp/WhatsApp/Media", "WhatsApp/Media"),
        ),
        Preset(
            "WA Business",
            listOf("Android/media/com.whatsapp.w4b/WhatsApp Business/Media", "WhatsApp Business/Media"),
        ),
        // Telegram's own cache lives in Android/data (blocked). With "Save to Gallery" on, it writes here instead.
        Preset(
            "Telegram",
            listOf(
                "Pictures/Telegram", "Movies/Telegram", "Music/Telegram",
                "Download/Telegram", "Documents/Telegram", "Telegram",
            ),
        ),
        Preset(
            "Viber",
            listOf("Pictures/Viber", "Movies/Viber", "Android/media/com.viber.voip/Viber", "Viber"),
        ),
        Preset(
            "Instagram",
            listOf("Pictures/Instagram", "Movies/Instagram", "DCIM/Instagram"),
        ),
        Preset(
            "Messenger",
            listOf("Pictures/Messenger", "Movies/Messenger"),
        ),
    )

    fun detect(preset: Preset): List<String> = preset.candidates.filter { Storage.resolve(it).isDirectory }
}
