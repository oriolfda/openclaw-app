package com.openclaw.app

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleManager {
    val supportedUiLocales = listOf(
        "auto",
        "en-GB", // EN_EN equivalent
        "en-US",
        "ca-ES",
        "es-ES",
        "gl-ES",
        "eu-ES", // Basc
    )

    fun apply(context: Context, code: String?): Context {
        val c = code ?: "auto"
        if (c == "auto") return context
        val parts = c.split("-")
        val locale = if (parts.size >= 2) Locale(parts[0], parts[1]) else Locale(parts[0])
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
