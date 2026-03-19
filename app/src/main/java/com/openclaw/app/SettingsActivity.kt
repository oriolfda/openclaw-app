package com.openclaw.app

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class SettingsActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        val prefs = newBase.getSharedPreferences("openclaw_app_prefs", android.content.Context.MODE_PRIVATE)
        val code = prefs.getString("ui_locale", "auto")
        super.attachBaseContext(LocaleManager.apply(newBase, code))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val root: ScrollView = findViewById(R.id.settingsRoot)
        val endpointEdit: EditText = findViewById(R.id.settingsEndpointEdit)
        val tokenEdit: EditText = findViewById(R.id.settingsTokenEdit)
        val themeSpinner: Spinner = findViewById(R.id.themeSpinner)
        val languageSpinner: Spinner = findViewById(R.id.languageSpinner)
        val showTranscriptionsCheck: CheckBox = findViewById(R.id.showTranscriptionsCheck)
        val saveButton: MaterialButton = findViewById(R.id.saveSettingsButton)
        val statusText: TextView = findViewById(R.id.settingsStatusText)

        val prefs = getSharedPreferences("openclaw_app_prefs", MODE_PRIVATE)
        val uiTheme = ThemeManager.byId(prefs.getString(ThemeManager.PREF_KEY, "html_match"))
        val isLight = uiTheme.screenBg == 0xFFF8FAFC.toInt()

        root.setBackgroundColor(uiTheme.screenBg)
        endpointEdit.setTextColor(uiTheme.messageTextColor)
        endpointEdit.setHintTextColor(uiTheme.messageHintColor)
        endpointEdit.setBackgroundResource(uiTheme.inputBg)
        tokenEdit.setTextColor(uiTheme.messageTextColor)
        tokenEdit.setHintTextColor(uiTheme.messageHintColor)
        tokenEdit.setBackgroundResource(uiTheme.inputBg)
        statusText.setTextColor(uiTheme.statusColor)
        showTranscriptionsCheck.setTextColor(uiTheme.messageTextColor)

        saveButton.backgroundTintList = ColorStateList.valueOf(uiTheme.sendTint)
        saveButton.setTextColor(uiTheme.sendText)

        endpointEdit.setText(prefs.getString("openclaw_endpoint", "http://192.168.0.102:8092/chat"))
        tokenEdit.setText(prefs.getString("openclaw_hook_token", ""))

        val themes = ThemeManager.themes
        val themeLabels = themes.map { it.label }
        val themeAdapter = themedAdapter(themeLabels, isLight)
        themeSpinner.adapter = themeAdapter

        val currentThemeId = prefs.getString(ThemeManager.PREF_KEY, "html_match")
        val selectedIndex = themes.indexOfFirst { it.id == currentThemeId }.coerceAtLeast(0)
        themeSpinner.setSelection(selectedIndex)

        val languageOptions = listOf(
            "auto" to getString(R.string.lang_auto),
            "en-GB" to "English (UK)",
            "en-US" to "English (US)",
            "ca-ES" to "Català",
            "es-ES" to "Español",
            "gl-ES" to "Galego",
            "eu-ES" to "Euskara",
        )
        val langAdapter = themedAdapter(languageOptions.map { it.second }, isLight)
        languageSpinner.adapter = langAdapter

        val currentLang = prefs.getString("ui_locale", "auto") ?: "auto"
        val langIndex = languageOptions.indexOfFirst { it.first == currentLang }.coerceAtLeast(0)
        languageSpinner.setSelection(langIndex)

        showTranscriptionsCheck.isChecked = prefs.getBoolean("show_transcriptions", true)

        saveButton.setOnClickListener {
            val endpoint = endpointEdit.text.toString().trim()
            val token = tokenEdit.text.toString().trim()
            val themeId = themes[themeSpinner.selectedItemPosition].id
            val selectedLang = languageOptions[languageSpinner.selectedItemPosition].first
            val showTranscriptions = showTranscriptionsCheck.isChecked

            if (endpoint.isBlank() || token.isBlank()) {
                statusText.text = getString(R.string.fill_endpoint_token)
                return@setOnClickListener
            }

            prefs.edit()
                .putString("openclaw_endpoint", endpoint)
                .putString("openclaw_hook_token", token)
                .putString(ThemeManager.PREF_KEY, themeId)
                .putString("ui_locale", selectedLang)
                .putBoolean("show_transcriptions", showTranscriptions)
                .apply()

            statusText.text = getString(R.string.saved_ok)
            setResult(RESULT_OK)
            recreate()
        }
    }

    private fun themedAdapter(items: List<String>, isLight: Boolean): ArrayAdapter<String> {
        val textColor = if (isLight) Color.parseColor("#0F172A") else Color.parseColor("#F3F4F6")
        val bgColor = if (isLight) Color.parseColor("#FFFFFF") else Color.parseColor("#111827")

        return object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent)
                (v as? TextView)?.setTextColor(textColor)
                v.setBackgroundColor(bgColor)
                return v
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getDropDownView(position, convertView, parent)
                (v as? TextView)?.setTextColor(textColor)
                v.setBackgroundColor(bgColor)
                return v
            }
        }.also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
    }
}
