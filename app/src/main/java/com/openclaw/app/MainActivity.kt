package com.openclaw.app

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.view.MotionEvent
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.MediaController
import android.widget.VideoView
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        val prefs = newBase.getSharedPreferences("openclaw_app_prefs", android.content.Context.MODE_PRIVATE)
        val code = prefs.getString("ui_locale", "auto")
        super.attachBaseContext(LocaleManager.apply(newBase, code))
    }

    data class AttachmentData(
        val name: String,
        val mime: String,
        val base64: String,
    )

    private lateinit var rootLayout: View
    private lateinit var topToolbar: MaterialToolbar
    private lateinit var composerRow: LinearLayout
    private lateinit var clipButton: ImageButton
    private lateinit var cameraButton: ImageButton
    private lateinit var messageInputContainer: LinearLayout
    private lateinit var messageEdit: EditText
    private lateinit var statusText: TextView
    private lateinit var chatRecycler: RecyclerView
    private lateinit var sendButton: ImageButton
    private lateinit var pendingAttachmentRow: LinearLayout
    private lateinit var pendingAttachmentPreview: ImageView
    private lateinit var pendingAttachmentText: TextView
    private lateinit var cancelAttachmentButton: Button
    private lateinit var micButton: ImageButton
    private lateinit var recordingControlsRow: LinearLayout
    private lateinit var recordDeleteButton: ImageButton
    private lateinit var recordPauseButton: ImageButton
    private lateinit var recordSendButton: ImageButton
    private lateinit var recordTimerText: TextView
    private lateinit var recordDotsText: TextView

    private lateinit var adapter: ChatAdapter
    private val messages = mutableListOf<ChatMessage>()
    private var pendingAttachment: AttachmentData? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentPlayingTs: Long? = null
    private var appliedUiLocale: String = "auto"

    private var mediaRecorder: MediaRecorder? = null
    private var currentRecordingFile: File? = null
    private var isRecording = false
    private var isRecordingPaused = false
    private var isRecordingLocked = false
    private var micStartY = 0f
    private var micStartX = 0f
    private var recordingStartMs = 0L
    private val recordingHandler = Handler(Looper.getMainLooper())

    private val pickMediaLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) handlePickedMedia(uri)
    }

    private val takePicturePreviewLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val b64 = bitmapToBase64(bitmap)
            pendingAttachment = AttachmentData(name = "camera-photo.jpg", mime = "image/jpeg", base64 = b64)
            updatePendingAttachmentUi()
            statusText.text = getString(R.string.photo_ready)
        }
    }

    private val captureVideoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data
        if (uri != null) handlePickedMedia(uri)
    }

    private val recordAudioLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data
        if (uri != null) handlePickedMedia(uri)
    }

    private val requestAudioPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            startPressRecording()
        } else {
            statusText.text = getString(R.string.mic_permission_required)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rootLayout = findViewById(R.id.rootLayout)
        topToolbar = findViewById(R.id.topToolbar)
        composerRow = findViewById(R.id.composerRow)
        clipButton = findViewById(R.id.clipButton)
        cameraButton = findViewById(R.id.cameraButton)
        messageInputContainer = findViewById(R.id.messageInputContainer)
        messageEdit = findViewById(R.id.messageEdit)
        statusText = findViewById(R.id.statusText)
        chatRecycler = findViewById(R.id.chatRecycler)
        sendButton = findViewById(R.id.sendButton)
        pendingAttachmentRow = findViewById(R.id.pendingAttachmentRow)
        pendingAttachmentPreview = findViewById(R.id.pendingAttachmentPreview)
        pendingAttachmentText = findViewById(R.id.pendingAttachmentText)
        cancelAttachmentButton = findViewById(R.id.cancelAttachmentButton)
        micButton = findViewById(R.id.micButton)
        recordingControlsRow = findViewById(R.id.recordingControlsRow)
        recordDeleteButton = findViewById(R.id.recordDeleteButton)
        recordPauseButton = findViewById(R.id.recordPauseButton)
        recordSendButton = findViewById(R.id.recordSendButton)
        recordTimerText = findViewById(R.id.recordTimerText)
        recordDotsText = findViewById(R.id.recordDotsText)

        appliedUiLocale = getSharedPreferences("openclaw_app_prefs", MODE_PRIVATE).getString("ui_locale", "auto") ?: "auto"
        runCatching { E2eeKeyManager(this).ensureLocalBundle() }

        val theme = currentTheme()
        adapter = ChatAdapter(
            messages,
            theme,
            onMessageClick = { msg ->
                when {
                    !msg.audioPath.isNullOrBlank() || !msg.audioUrl.isNullOrBlank() || !msg.ttsText.isNullOrBlank() -> {
                        toggleAudioPlayback(msg)
                    }
                    !msg.imagePath.isNullOrBlank() -> {
                        showImagePreview(msg.imagePath)
                    }
                    !msg.videoPath.isNullOrBlank() -> {
                        openVideo(msg.videoPath)
                    }
                    Regex("<\\s*[a-zA-Z][^>]*>").containsMatchIn(msg.text) -> {
                        showHtmlPreview(msg.text)
                    }
                }
            },
            onAudioTranscribeClick = { msg ->
                requestTranscription(msg)
            }
        )
        chatRecycler.layoutManager = LinearLayoutManager(this)
        chatRecycler.adapter = adapter
        applyTheme(theme)
        val showTranscriptions = getSharedPreferences("openclaw_app_prefs", MODE_PRIVATE).getBoolean("show_transcriptions", true)
        adapter.setShowTranscriptionOption(!showTranscriptions)

        loadHistory()
        consumeSharedText(intent)
        updatePendingAttachmentUi()
        updateComposerActionButton()

        messageEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                updateComposerActionButton()
            }
        })

        messageEdit.setOnFocusChangeListener { _, hasFocus ->
            messageInputContainer.isSelected = hasFocus
        }

        topToolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_status -> {
                    fetchContextStatus()
                    true
                }
                R.id.menu_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                R.id.menu_about -> {
                    showAboutDialog()
                    true
                }
                R.id.menu_clear_chat -> {
                    messages.clear()
                    adapter.notifyDataSetChanged()
                    saveHistory()
                    statusText.text = getString(R.string.status_chat_cleared)
                    true
                }
                else -> false
            }
        }

        clipButton.setOnClickListener {
            pickMediaLauncher.launch("*/*")
        }

        cameraButton.setOnClickListener {
            takePicturePreviewLauncher.launch(null)
        }

        cameraButton.setOnLongClickListener {
            val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
            captureVideoLauncher.launch(intent)
            true
        }

        cancelAttachmentButton.setOnClickListener {
            pendingAttachment = null
            updatePendingAttachmentUi()
            statusText.text = getString(R.string.attachment_removed)
        }

        micButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    micStartY = event.rawY
                    micStartX = event.rawX
                    ensureAudioPermissionAndStart()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isRecording && !isRecordingLocked) {
                        val deltaUp = micStartY - event.rawY
                        val deltaLeft = micStartX - event.rawX

                        if (deltaLeft > 120f) {
                            cancelRecording()
                            statusText.text = getString(R.string.recording_cancelled_swipe)
                            return@setOnTouchListener true
                        }

                        if (deltaUp > 140f) {
                            isRecordingLocked = true
                            statusText.text = getString(R.string.recording_locked)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isRecording && !isRecordingLocked) {
                        stopRecordingAndAttach(sendNow = true)
                    }
                    true
                }
                else -> false
            }
        }

        recordDeleteButton.setOnClickListener {
            cancelRecording()
        }

        recordPauseButton.setOnClickListener {
            togglePauseRecording()
        }

        recordSendButton.setOnClickListener {
            if (isRecording) stopRecordingAndAttach(sendNow = true)
        }

        sendButton.setOnClickListener {
            val prefs = getSharedPreferences("openclaw_app_prefs", MODE_PRIVATE)
            val endpoint = prefs.getString("openclaw_endpoint", "").orEmpty().trim()
            val token = prefs.getString("openclaw_hook_token", "").orEmpty().trim()
            val message = messageEdit.text.toString().trim()

            if (endpoint.isBlank()) {
                statusText.text = getString(R.string.status_missing_endpoint)
                return@setOnClickListener
            }
            if (token.isBlank()) {
                statusText.text = getString(R.string.status_missing_token)
                return@setOnClickListener
            }
            if (message.isBlank() && pendingAttachment == null) {
                return@setOnClickListener
            }

            val previewText = buildString {
                if (message.isNotBlank()) append(message)
                pendingAttachment?.let {
                    if (isNotBlank()) append("\n")
                    append("📎 ${it.name}")
                }
            }

            val attachmentToSend = pendingAttachment
            var sentAudioPath: String? = null
            var sentImagePath: String? = null
            var sentVideoPath: String? = null
            if (attachmentToSend?.mime?.startsWith("audio/") == true) {
                try {
                    val bytes = Base64.decode(attachmentToSend.base64, Base64.DEFAULT)
                    val f = File(cacheDir, "sent-audio-${System.currentTimeMillis()}.m4a")
                    f.writeBytes(bytes)
                    sentAudioPath = f.absolutePath
                } catch (_: Exception) {
                }
            } else if (attachmentToSend?.mime?.startsWith("image/") == true) {
                try {
                    val bytes = Base64.decode(attachmentToSend.base64, Base64.DEFAULT)
                    val f = File(cacheDir, "sent-image-${System.currentTimeMillis()}.jpg")
                    f.writeBytes(bytes)
                    sentImagePath = f.absolutePath
                } catch (_: Exception) {
                }
            } else if (attachmentToSend?.mime?.startsWith("video/") == true) {
                try {
                    val bytes = Base64.decode(attachmentToSend.base64, Base64.DEFAULT)
                    val f = File(cacheDir, "sent-video-${System.currentTimeMillis()}.mp4")
                    f.writeBytes(bytes)
                    sentVideoPath = f.absolutePath
                } catch (_: Exception) {
                }
            }

            addMessage(
                ChatMessage(
                    "user",
                    previewText.ifBlank { getString(R.string.attachment_placeholder) },
                    audioPath = sentAudioPath,
                    imagePath = sentImagePath,
                    videoPath = sentVideoPath,
                )
            )
            messageEdit.setText("")
            addMessage(ChatMessage("typing", ""))

            sendToOpenClaw(endpoint, token, message, attachmentToSend)
            pendingAttachment = null
            updatePendingAttachmentUi()
        }
    }

    override fun onResume() {
        super.onResume()

        val prefs = getSharedPreferences("openclaw_app_prefs", MODE_PRIVATE)
        val currentLocale = prefs.getString("ui_locale", "auto") ?: "auto"
        if (currentLocale != appliedUiLocale) {
            appliedUiLocale = currentLocale
            recreate()
            return
        }

        val theme = currentTheme()
        applyTheme(theme)
        adapter.setTheme(theme)
        val showTranscriptions = prefs.getBoolean("show_transcriptions", true)
        adapter.setShowTranscriptionOption(!showTranscriptions)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRecording) {
            cancelRecording()
        } else {
            cleanupRecorderState()
        }
        try { mediaPlayer?.release() } catch (_: Exception) {}
        mediaPlayer = null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeSharedText(intent)
    }

    private fun consumeSharedText(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val shared = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
            if (shared.isNotBlank()) {
                val current = messageEdit.text.toString().trim()
                val combined = if (current.isBlank()) shared else "$current\n\n$shared"
                messageEdit.setText(combined)
                statusText.text = getString(R.string.status_shared_text_loaded)
            }
        }
    }

    private fun handlePickedMedia(uri: Uri) {
        try {
            val mime = contentResolver.getType(uri).orEmpty()
            if (!(mime.startsWith("image/") || mime.startsWith("video/") || mime.startsWith("audio/"))) {
                statusText.text = getString(R.string.only_image_video_audio)
                return
            }

            val name = queryName(uri) ?: getString(R.string.attachment_generic_name)
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: run {
                statusText.text = getString(R.string.file_read_error)
                return
            }
            if (bytes.size > 12 * 1024 * 1024) {
                statusText.text = getString(R.string.file_too_large)
                return
            }

            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            pendingAttachment = AttachmentData(name = name, mime = mime, base64 = b64)
            updatePendingAttachmentUi()
            statusText.text = getString(R.string.attachment_ready, name)
        } catch (e: Exception) {
            statusText.text = getString(R.string.attachment_error, e.message)
        }
    }

    private fun queryName(uri: Uri): String? {
        return contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }

    private fun requestTranscription(msg: ChatMessage) {
        if (!msg.transcriptText.isNullOrBlank()) {
            adapter.toggleTranscript(msg.ts)
            return
        }

        thread {
            try {
                val bytes = when {
                    !msg.audioPath.isNullOrBlank() -> {
                        val f = File(msg.audioPath)
                        if (!f.exists()) null else f.readBytes()
                    }
                    !msg.audioUrl.isNullOrBlank() -> {
                        URL(msg.audioUrl).openStream().use { it.readBytes() }
                    }
                    else -> null
                }
                if (bytes == null) {
                    runOnUiThread { statusText.text = getString(R.string.local_audio_not_found) }
                    return@thread
                }

                val prefs = getSharedPreferences("openclaw_app_prefs", MODE_PRIVATE)
                val endpoint = prefs.getString("openclaw_endpoint", "").orEmpty().trim()
                val token = prefs.getString("openclaw_hook_token", "").orEmpty().trim()
                if (endpoint.isBlank() || token.isBlank()) {
                    runOnUiThread { statusText.text = getString(R.string.status_configure_endpoint_token) }
                    return@thread
                }

                val attachment = JSONObject().apply {
                    put("name", "transcription-audio.m4a")
                    put("mime", "audio/mp4")
                    put("dataBase64", Base64.encodeToString(bytes, Base64.NO_WRAP))
                }

                val payload = JSONObject().apply {
                    put("message", getString(R.string.transcribe_only_prompt))
                    put("sessionId", "openclaw-app-chat")
                    put("prefs", JSONObject().apply {
                        put("showTranscription", true)
                    })
                    put("attachment", attachment)
                }

                val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $token")
                    connectTimeout = 20000
                    readTimeout = 120000
                    doOutput = true
                }
                OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }
                val code = conn.responseCode
                val body = if (code in 200..299) conn.inputStream.bufferedReader().use(BufferedReader::readText)
                else conn.errorStream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
                conn.disconnect()

                val transcript = parseAssistantText(body, code)
                runOnUiThread {
                    adapter.setTranscript(msg.ts, transcript, true)
                    statusText.text = getString(R.string.transcription_ready)
                }
            } catch (e: Exception) {
                runOnUiThread { statusText.text = getString(R.string.attachment_error, e.message) }
            }
        }
    }

    private fun toggleAudioPlayback(msg: ChatMessage) {
        // same bubble clicked: toggle pause/resume
        if (currentPlayingTs == msg.ts && mediaPlayer != null) {
            val mp = mediaPlayer!!
            if (mp.isPlaying) {
                mp.pause()
                adapter.setPlayingMessage(null)
                statusText.text = getString(R.string.audio_paused)
            } else {
                mp.start()
                adapter.setPlayingMessage(msg.ts)
                statusText.text = getString(R.string.playing_audio)
            }
            return
        }

        when {
            !msg.audioPath.isNullOrBlank() -> {
                val f = File(msg.audioPath)
                if (f.exists()) playLocalAudio(f, msg.ts) else statusText.text = getString(R.string.local_audio_not_found)
            }
            !msg.audioUrl.isNullOrBlank() -> tryPlayRemoteAudio(msg.audioUrl, msg.ts)
        }
    }

    private fun playLocalAudio(file: File, ts: Long? = null) {
        try {
            mediaPlayer?.release()
            currentPlayingTs = ts
            adapter.setPlayingMessage(ts)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnPreparedListener { it.start() }
                setOnCompletionListener { mp ->
                    mp.release()
                    mediaPlayer = null
                    currentPlayingTs = null
                    adapter.setPlayingMessage(null)
                }
                prepareAsync()
            }
            statusText.text = getString(R.string.playing_audio)
        } catch (e: Exception) {
            statusText.text = getString(R.string.audio_play_error, e.message)
            currentPlayingTs = null
            adapter.setPlayingMessage(null)
        }
    }

    private fun tryPlayRemoteAudio(url: String, ts: Long? = null) {
        val u = url.lowercase()
        val looksAudio = u.endsWith(".mp3") || u.endsWith(".m4a") || u.endsWith(".wav") || u.endsWith(".ogg") || u.contains("audio") || u.contains("/media/")
        if (!looksAudio) return
        try {
            mediaPlayer?.release()
            currentPlayingTs = ts
            adapter.setPlayingMessage(ts)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(url)
                setOnPreparedListener { it.start() }
                setOnCompletionListener { mp ->
                    mp.release()
                    mediaPlayer = null
                    currentPlayingTs = null
                    adapter.setPlayingMessage(null)
                }
                prepareAsync()
            }
            statusText.text = getString(R.string.playing_reply_audio)
        } catch (_: Exception) {
            currentPlayingTs = null
            adapter.setPlayingMessage(null)
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun updatePendingAttachmentUi() {
        val att = pendingAttachment
        if (att == null) {
            pendingAttachmentRow.visibility = View.GONE
            pendingAttachmentPreview.setImageResource(android.R.drawable.ic_menu_gallery)
        } else {
            pendingAttachmentRow.visibility = View.VISIBLE
            pendingAttachmentText.text = "📎 ${att.name}"

            when {
                att.mime.startsWith("image/") -> {
                    try {
                        val bytes = Base64.decode(att.base64, Base64.DEFAULT)
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bmp != null) pendingAttachmentPreview.setImageBitmap(bmp)
                        else pendingAttachmentPreview.setImageResource(android.R.drawable.ic_menu_gallery)
                    } catch (_: Exception) {
                        pendingAttachmentPreview.setImageResource(android.R.drawable.ic_menu_gallery)
                    }
                }
                att.mime.startsWith("video/") -> pendingAttachmentPreview.setImageResource(android.R.drawable.ic_media_play)
                att.mime.startsWith("audio/") -> pendingAttachmentPreview.setImageResource(android.R.drawable.ic_btn_speak_now)
                else -> pendingAttachmentPreview.setImageResource(android.R.drawable.ic_menu_save)
            }
        }
        updateComposerActionButton()
    }

    private fun updateComposerActionButton() {
        val hasText = messageEdit.text?.toString()?.trim()?.isNotEmpty() == true
        val showSend = hasText || pendingAttachment != null
        sendButton.visibility = if (showSend) View.VISIBLE else View.GONE
        micButton.visibility = if (showSend) View.GONE else View.VISIBLE
    }

    private fun ensureAudioPermissionAndStart() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startPressRecording()
        } else {
            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startPressRecording() {
        if (isRecording) return
        try {
            val file = File(cacheDir, "voice-${System.currentTimeMillis()}.m4a")
            val rec = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = rec
            currentRecordingFile = file
            isRecording = true
            isRecordingPaused = false
            isRecordingLocked = false
            recordingStartMs = System.currentTimeMillis()
            composerRow.visibility = View.GONE
            recordingControlsRow.visibility = View.VISIBLE
            statusText.text = getString(R.string.recording_swipe_up_lock)
            startRecordingTicker()
        } catch (e: Exception) {
            statusText.text = getString(R.string.recording_start_error, e.message)
            cleanupRecorderState()
        }
    }

    private fun startRecordingTicker() {
        recordingHandler.removeCallbacksAndMessages(null)
        recordingHandler.post(object : Runnable {
            override fun run() {
                if (!isRecording) return
                val elapsed = ((System.currentTimeMillis() - recordingStartMs) / 1000).toInt().coerceAtLeast(0)
                val mm = elapsed / 60
                val ss = elapsed % 60
                recordTimerText.text = String.format("%02d:%02d", mm, ss)
                recordingHandler.postDelayed(this, 500)
            }
        })
    }

    private fun togglePauseRecording() {
        val rec = mediaRecorder ?: return
        try {
            if (!isRecordingPaused) {
                rec.pause()
                isRecordingPaused = true
                recordPauseButton.setImageResource(R.drawable.ic_play_min)
                statusText.text = getString(R.string.recording_paused)
            } else {
                rec.resume()
                isRecordingPaused = false
                recordPauseButton.setImageResource(R.drawable.ic_pause_min)
                statusText.text = getString(R.string.recording_in_progress)
            }
        } catch (_: Exception) {
            // Some devices may not support pause/resume reliably.
        }
    }

    private fun cancelRecording() {
        try {
            mediaRecorder?.stop()
        } catch (_: Exception) {
        }
        currentRecordingFile?.delete()
        cleanupRecorderState()
        statusText.text = getString(R.string.recording_discarded)
    }

    private fun stopRecordingAndAttach(sendNow: Boolean) {
        val file = currentRecordingFile
        try {
            mediaRecorder?.stop()
        } catch (_: Exception) {
        }
        cleanupRecorderState()

        if (file == null || !file.exists()) {
            statusText.text = getString(R.string.audio_save_error)
            return
        }

        val bytes = file.readBytes()
        pendingAttachment = AttachmentData(
            name = "voice-${System.currentTimeMillis()}.m4a",
            mime = "audio/mp4",
            base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        )
        updatePendingAttachmentUi()
        statusText.text = getString(R.string.audio_ready)

        if (sendNow) {
            sendButton.performClick()
        }

        file.delete()
    }

    private fun cleanupRecorderState() {
        try { mediaRecorder?.release() } catch (_: Exception) {}
        mediaRecorder = null
        currentRecordingFile = null
        isRecording = false
        isRecordingPaused = false
        isRecordingLocked = false
        recordPauseButton.setImageResource(R.drawable.ic_pause_min)
        recordTimerText.text = "0:00"
        recordingControlsRow.visibility = View.GONE
        composerRow.visibility = View.VISIBLE
        recordingHandler.removeCallbacksAndMessages(null)
        updateComposerActionButton()
    }

    private fun currentTheme(): ThemeManager.UiTheme {
        val prefs = getSharedPreferences("openclaw_app_prefs", MODE_PRIVATE)
        return ThemeManager.byId(prefs.getString(ThemeManager.PREF_KEY, "html_match"))
    }

    private fun applyTheme(theme: ThemeManager.UiTheme) {
        rootLayout.setBackgroundColor(theme.screenBg)
        topToolbar.setBackgroundColor(theme.screenBg)
        topToolbar.setTitleTextColor(theme.titleColor)
        topToolbar.overflowIcon?.setTint(theme.menuDotsColor)
        statusText.setTextColor(theme.statusColor)
        messageEdit.setTextColor(theme.messageTextColor)
        messageEdit.setHintTextColor(theme.messageHintColor)
        messageInputContainer.setBackgroundResource(theme.inputBg)
        clipButton.setColorFilter(theme.sendTint)
        cameraButton.setColorFilter(theme.sendTint)
        micButton.backgroundTintList = android.content.res.ColorStateList.valueOf(theme.sendTint)
        micButton.setColorFilter(theme.sendText)
        sendButton.backgroundTintList = android.content.res.ColorStateList.valueOf(theme.sendTint)
        sendButton.setColorFilter(theme.sendText)
        recordDeleteButton.setColorFilter(theme.statusColor)
        recordPauseButton.setColorFilter(0xFFFF4D67.toInt())
        recordSendButton.backgroundTintList = android.content.res.ColorStateList.valueOf(theme.sendTint)
        recordSendButton.setColorFilter(theme.sendText)
        recordTimerText.setTextColor(theme.statusColor)
        recordDotsText.setTextColor(theme.statusColor)
    }

    private fun extractUrls(text: String): List<String> {
        val pattern = Pattern.compile("(https?://[^\\s]+)")
        val matcher = pattern.matcher(text)
        val urls = mutableListOf<String>()
        while (matcher.find()) matcher.group(1)?.let { urls.add(it) }
        return urls.distinct()
    }

    private fun sendToOpenClaw(endpoint: String, token: String, message: String, attachment: AttachmentData?) {
        statusText.text = getString(R.string.status_sending)

        thread {
            try {
                val prefs = getSharedPreferences("openclaw_app_prefs", MODE_PRIVATE)
                val showTranscriptions = prefs.getBoolean("show_transcriptions", true)

                val urls = extractUrls(message)
                val payloadText = if (urls.isEmpty()) message else "$message\n\nURLs detectades: ${urls.joinToString(", ")}"
                val bridgeTarget = fetchE2eeBridgeTarget(endpoint, token)
                val bridgePub = bridgeTarget?.first
                val bridgeOtkId = bridgeTarget?.second
                var encResult: DevE2ee.EncryptResult? = null
                var messageCounter = 0

                val payload = JSONObject().apply {
                    put("sessionId", "openclaw-app-chat")
                    put("prefs", JSONObject().apply {
                        put("showTranscription", showTranscriptions)
                    })

                    if (!bridgePub.isNullOrBlank()) {
                        val nextCounter = prefs.getInt("e2ee_send_counter", 0) + 1
                        prefs.edit().putInt("e2ee_send_counter", nextCounter).apply()
                        messageCounter = nextCounter
                        encResult = DevE2ee.encryptForBridge(payloadText, bridgePub, "openclaw-app-chat", bridgeOtkId, nextCounter)
                        put("message", "")
                        put("e2ee", encResult!!.envelope)
                    } else {
                        put("message", payloadText)
                    }

                    attachment?.let {
                        if (encResult != null) {
                            put("e2eeAttachment", DevE2ee.encryptAttachment(it.base64, encResult!!.responseKey, it.name, it.mime, "openclaw-app-chat", messageCounter))
                        } else {
                            put("attachment", JSONObject().apply {
                                put("name", it.name)
                                put("mime", it.mime)
                                put("dataBase64", it.base64)
                            })
                        }
                    }
                }

                val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $token")
                    connectTimeout = 20000
                    readTimeout = 120000
                    doOutput = true
                }

                OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }

                val code = conn.responseCode
                val body = try {
                    if (code in 200..299) conn.inputStream.bufferedReader().use(BufferedReader::readText)
                    else conn.errorStream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
                } catch (_: Exception) { "" }

                runOnUiThread {
                    val prefs = getSharedPreferences("openclaw_app_prefs", MODE_PRIVATE)
                    val showTranscriptions = prefs.getBoolean("show_transcriptions", true)

                    val assistantTextRaw = try {
                        val obj = JSONObject(body)
                        if (obj.has("e2eeReply") && encResult != null) {
                            val env = obj.getJSONObject("e2eeReply")
                            val inCounter = env.optInt("counter", 0)
                            if (inCounter > 0 && !acceptIncomingCounter(prefs, inCounter)) {
                                "[E2EE] Response dropped (replay/window)"
                            } else {
                                DevE2ee.decryptWithKey(encResult!!.responseKey, env)
                            }
                        } else {
                            parseAssistantText(body, code)
                        }
                    } catch (_: Exception) {
                        parseAssistantText(body, code)
                    }
                    val mediaUrl = try {
                        JSONObject(body).optString("mediaUrl", "")
                    } catch (_: Exception) { "" }
                    val (assistantText, ttsText) = extractTtsBlock(assistantTextRaw)

                    if (!showTranscriptions && mediaUrl.isNotBlank()) {
                        val audioMsg = ChatMessage("assistant", getString(R.string.reply_audio), audioUrl = mediaUrl)
                        adapter.replaceLast(audioMsg)
                        tryPlayRemoteAudio(mediaUrl, audioMsg.ts)
                    } else {
                        adapter.replaceLast(ChatMessage("assistant", assistantText, ttsText = ttsText))
                        if (mediaUrl.isNotBlank()) {
                            val audioMsg = ChatMessage("assistant", getString(R.string.reply_audio), audioUrl = mediaUrl)
                            addMessage(audioMsg)
                            tryPlayRemoteAudio(mediaUrl, audioMsg.ts)
                        } else if (!ttsText.isNullOrBlank()) {
                            addMessage(ChatMessage("assistant", getString(R.string.tts_pending_server_voice)))
                        }
                    }

                    statusText.text = if (code in 200..299) getString(R.string.status_sent_ok, code) else getString(R.string.status_http_error, code)
                    saveHistory()
                    scrollBottom()
                }
                conn.disconnect()
            } catch (e: Exception) {
                runOnUiThread {
                    adapter.replaceLast(ChatMessage("assistant", getString(R.string.connection_error, e.message)))
                    statusText.text = getString(R.string.status_error, e.message)
                    saveHistory()
                    scrollBottom()
                }
            }
        }
    }

    private fun loadSeenCounters(prefs: android.content.SharedPreferences, key: String): MutableSet<Int> {
        val raw = prefs.getString(key, "").orEmpty()
        if (raw.isBlank()) return mutableSetOf()
        return try {
            if (raw.trimStart().startsWith("[")) {
                val arr = org.json.JSONArray(raw)
                MutableList(arr.length()) { i -> arr.optInt(i, -1) }
                    .filter { it > 0 }
                    .toMutableSet()
            } else {
                raw.split(',').mapNotNull { it.toIntOrNull() }.filter { it > 0 }.toMutableSet()
            }
        } catch (_: Exception) {
            mutableSetOf()
        }
    }

    private fun saveSeenCounters(prefs: android.content.SharedPreferences, key: String, values: List<Int>) {
        val arr = org.json.JSONArray()
        values.forEach { arr.put(it) }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    private fun acceptIncomingCounter(prefs: android.content.SharedPreferences, counter: Int, sessionId: String = "openclaw-app-chat", window: Int = 64): Boolean {
        val maxKey = "e2ee_in_max_${sessionId}"
        val seenKey = "e2ee_in_seen_${sessionId}"
        val skippedKey = "e2ee_in_skipped_${sessionId}"

        val maxIn = prefs.getInt(maxKey, 0)
        val seen = loadSeenCounters(prefs, seenKey)
        val skipped = loadSeenCounters(prefs, skippedKey)

        if (counter <= 0) return false
        if (seen.contains(counter)) return false
        if (counter < maxIn - window) return false

        if (counter > maxIn + 1) {
            for (c in (maxIn + 1) until counter) skipped.add(c)
        }

        seen.add(counter)
        skipped.remove(counter)

        val newMax = kotlin.math.max(maxIn, counter)
        val floor = newMax - window
        val keptSeen = seen.filter { it >= floor }.sorted()
        val keptSkipped = skipped.filter { it >= floor }.sorted()

        prefs.edit().putInt(maxKey, newMax).apply()
        saveSeenCounters(prefs, seenKey, keptSeen)
        saveSeenCounters(prefs, skippedKey, keptSkipped)
        return true
    }

    private fun fetchE2eeBridgeTarget(endpoint: String, token: String): Pair<String, String?>? {
        return try {
            val bundleUrl = endpoint.replace("/chat", "/e2ee/prekey-bundle")
            val conn = (URL(bundleUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
                connectTimeout = 8000
                readTimeout = 10000
            }
            val code = conn.responseCode
            val body = if (code in 200..299) conn.inputStream.bufferedReader().use(BufferedReader::readText)
            else conn.errorStream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            conn.disconnect()
            if (code !in 200..299) return null

            val obj = JSONObject(body)
            val e2ee = obj.optJSONObject("e2ee") ?: return null
            val bundle = e2ee.optJSONObject("bundle") ?: return null
            val signPub = bundle.optString("identitySignKey", "")
            val spk = bundle.optJSONObject("signedPreKey") ?: return null
            val spkPub = spk.optString("publicKey", "")
            val spkSig = spk.optString("signature", "")
            val otkArr = bundle.optJSONArray("oneTimePreKeys")
            val otkId = if (otkArr != null && otkArr.length() > 0) {
                otkArr.optJSONObject(0)?.optString("id", "")?.ifBlank { null }
            } else null

            if (spkPub.isBlank() || signPub.isBlank() || spkSig.isBlank()) return null
            if (!DevE2ee.verifySignedPreKey(signPub, spkPub, spkSig)) return null
            Pair(spkPub, otkId)
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchContextStatus() {
        val prefs = getSharedPreferences("openclaw_app_prefs", MODE_PRIVATE)
        val endpoint = prefs.getString("openclaw_endpoint", "").orEmpty().trim()
        val token = prefs.getString("openclaw_hook_token", "").orEmpty().trim()
        if (endpoint.isBlank() || token.isBlank()) {
            statusText.text = getString(R.string.status_configure_endpoint_token)
            return
        }

        val statusUrl = endpoint.replace("/chat", "/status")
        addMessage(ChatMessage("assistant", getString(R.string.checking_context_status)))

        thread {
            try {
                val conn = (URL(statusUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer $token")
                    connectTimeout = 12000
                    readTimeout = 15000
                }
                val code = conn.responseCode
                val body = try {
                    if (code in 200..299) conn.inputStream.bufferedReader().use(BufferedReader::readText)
                    else conn.errorStream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
                } catch (_: Exception) { "" }

                runOnUiThread {
                    val msg = parseStatusText(body, code)
                    addMessage(ChatMessage("assistant", msg))
                    statusText.text = if (code in 200..299) getString(R.string.status_context_received) else getString(R.string.status_context_error, code)
                }
                conn.disconnect()
            } catch (e: Exception) {
                runOnUiThread {
                    addMessage(ChatMessage("assistant", getString(R.string.context_check_error, e.message)))
                }
            }
        }
    }

    private fun parseStatusText(body: String, code: Int): String {
        if (body.isBlank()) return getString(R.string.no_context_data, code)
        return try {
            val obj = JSONObject(body)
            if (!obj.optBoolean("ok", false)) {
                return getString(R.string.context_read_error, obj.optString("error", "error"))
            }
            val ctx = obj.optJSONObject("context") ?: return getString(R.string.context_incomplete_response)
            val used = ctx.optLong("usedTokens", -1)
            val max = ctx.optLong("maxTokens", -1)
            val usedPct = ctx.optDouble("usedPercent", -1.0)
            val free = ctx.optLong("freeTokens", -1)
            val freePct = ctx.optDouble("freePercent", -1.0)
            val model = ctx.optString("model", "?")

            val usedPctText = if (usedPct >= 0) String.format("%.1f", usedPct) else "?"
            val freePctText = if (freePct >= 0) String.format("%.1f", freePct) else "?"

            getString(
                R.string.context_status_template,
                model,
                used.toString(),
                max.toString(),
                usedPctText,
                free.toString(),
                freePctText,
            )
        } catch (_: Exception) {
            getString(R.string.context_parse_error)
        }
    }

    private fun parseAssistantText(body: String, code: Int): String {
        if (body.isBlank()) return if (code in 200..299) getString(R.string.message_sent_ok) else getString(R.string.error_http_code, code)
        return try {
            val obj = JSONObject(body)
            val core = when {
                obj.has("reply") -> obj.optString("reply")
                obj.has("response") -> obj.optString("response")
                obj.has("message") -> obj.optString("message")
                obj.has("text") -> obj.optString("text")
                obj.has("ok") && !obj.optBoolean("ok", false) -> {
                    val err = obj.optString("error", getString(R.string.unknown_error))
                    val details = obj.optString("details", "")
                    getString(R.string.bridge_error, err, details)
                }
                obj.has("ok") -> getString(R.string.message_sent_ok)
                else -> body
            }
            core
        } catch (_: Exception) {
            body
        }
    }

    private fun extractTtsBlock(text: String): Pair<String, String?> {
        val inline = Regex("\\[\\[tts:(.+?)\\]\\]", RegexOption.DOT_MATCHES_ALL).find(text)
        if (inline != null) {
            val ttsText = inline.groupValues[1].trim()
            val cleaned = text.replace(inline.value, "").trim().ifBlank { getString(R.string.text_response_received) }
            return cleaned to ttsText
        }

        val block = Regex("\\[\\[tts:text\\]\\](.+?)\\[\\[/tts:text\\]\\]", RegexOption.DOT_MATCHES_ALL).find(text)
        if (block != null) {
            val ttsText = block.groupValues[1].trim()
            val cleaned = text.replace(block.value, "").trim().ifBlank { getString(R.string.text_response_received) }
            return cleaned to ttsText
        }

        return text to null
    }


    private fun showImagePreview(path: String?) {
        if (path.isNullOrBlank()) return
        val f = File(path)
        if (!f.exists()) return
        val image = ImageView(this)
        image.setImageBitmap(BitmapFactory.decodeFile(path))
        image.adjustViewBounds = true
        image.scaleType = ImageView.ScaleType.FIT_CENTER
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.image_preview_title))
            .setView(image)
            .setPositiveButton(getString(R.string.close), null)
            .show()
    }

    private fun openVideo(path: String?) {
        if (path.isNullOrBlank()) return
        val f = File(path)
        if (!f.exists()) return

        val vv = VideoView(this)
        val uri = Uri.fromFile(f)
        val controller = MediaController(this)
        controller.setAnchorView(vv)
        vv.setMediaController(controller)
        vv.setVideoURI(uri)
        vv.setOnPreparedListener { it.start() }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.video_preview_title))
            .setView(vv)
            .setPositiveButton(getString(R.string.close), null)
            .show()
    }

    private fun showHtmlPreview(html: String) {
        val webView = WebView(this)
        val ws: WebSettings = webView.settings
        ws.javaScriptEnabled = false
        ws.domStorageEnabled = false
        ws.allowFileAccess = false
        ws.allowContentAccess = false

        webView.loadDataWithBaseURL(
            null,
            "<html><body style='background:#111827;color:#e5e7eb;font-family:Inter,Arial,sans-serif;'>$html</body></html>",
            "text/html",
            "utf-8",
            null
        )

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.html_preview_title))
            .setView(webView)
            .setPositiveButton(getString(R.string.close), null)
            .show()
    }

    private fun showAboutDialog() {
        val pkg = packageManager.getPackageInfo(packageName, 0)
        val versionName = pkg.versionName ?: "?"
        val versionCode = pkg.longVersionCode

        val info = buildString {
            appendLine(getString(R.string.app_name))
            appendLine(getString(R.string.about_version, versionName, versionCode))
            appendLine(getString(R.string.about_bridge))
            appendLine(getString(R.string.about_features))
            appendLine("")
            appendLine(getString(R.string.about_repo))
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.menu_about))
            .setMessage(info)
            .setPositiveButton(getString(R.string.close), null)
            .show()
    }

    private fun addMessage(msg: ChatMessage) {
        adapter.add(msg)
        saveHistory()
        scrollBottom()
    }

    private fun scrollBottom() {
        if (messages.isNotEmpty()) chatRecycler.scrollToPosition(messages.lastIndex)
    }

    private fun loadHistory() {
        val prefs = getSharedPreferences("openclaw_app_prefs", MODE_PRIVATE)
        val raw = prefs.getString("chat_history", "[]").orEmpty()
        messages.clear()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val role = o.optString("role", "assistant")
                if (role != "typing") {
                    messages.add(
                        ChatMessage(
                            role = role,
                            text = o.optString("text", ""),
                            ts = o.optLong("ts", 0L),
                            audioPath = o.optString("audioPath", "").ifBlank { null },
                            audioUrl = o.optString("audioUrl", "").ifBlank { null },
                            ttsText = o.optString("ttsText", "").ifBlank { null },
                            imagePath = o.optString("imagePath", "").ifBlank { null },
                            videoPath = o.optString("videoPath", "").ifBlank { null },
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        adapter.notifyDataSetChanged()
        scrollBottom()
    }

    private fun saveHistory() {
        val arr = JSONArray()
        messages.takeLast(200).forEach {
            arr.put(JSONObject().apply {
                put("role", it.role)
                put("text", it.text)
                put("ts", it.ts)
                put("audioPath", it.audioPath ?: "")
                put("audioUrl", it.audioUrl ?: "")
                put("ttsText", it.ttsText ?: "")
                put("imagePath", it.imagePath ?: "")
                put("videoPath", it.videoPath ?: "")
            })
        }
        getSharedPreferences("openclaw_app_prefs", MODE_PRIVATE)
            .edit()
            .putString("chat_history", arr.toString())
            .apply()
    }
}
