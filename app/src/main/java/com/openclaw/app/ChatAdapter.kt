package com.openclaw.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale
import kotlin.concurrent.thread

class ChatAdapter(
    private val items: MutableList<ChatMessage>,
    private var theme: ThemeManager.UiTheme,
    private val onMessageClick: ((ChatMessage) -> Unit)? = null,
    private val onAudioTranscribeClick: ((ChatMessage) -> Unit)? = null,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var playingMessageTs: Long? = null
    private var showTranscriptionOption: Boolean = false
    private val durationCache = mutableMapOf<Long, String>()
    private val durationLoading = mutableSetOf<Long>()

    companion object {
        private const val VIEW_USER = 1
        private const val VIEW_BOT = 2
        private const val VIEW_TYPING = 3
        private const val VIEW_HTML = 4
        private const val VIEW_IMAGE_USER = 5
        private const val VIEW_AUDIO = 6
    }

    class MessageVH(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.messageText)
    }

    class TypingVH(view: View) : RecyclerView.ViewHolder(view) {
        val d1: TextView = view.findViewById(R.id.dot1)
        val d2: TextView = view.findViewById(R.id.dot2)
        val d3: TextView = view.findViewById(R.id.dot3)
    }

    class HtmlVH(view: View) : RecyclerView.ViewHolder(view) {
        val bubble: View = view.findViewById(R.id.htmlBubble)
        val web: WebView = view.findViewById(R.id.htmlWeb)
        val label: TextView = view.findViewById(R.id.htmlLabel)
        val hint: TextView = view.findViewById(R.id.htmlOpenHint)
    }

    class ImageUserVH(view: View) : RecyclerView.ViewHolder(view) {
        val bubble: View = view.findViewById(R.id.imageBubble)
        val image: ImageView = view.findViewById(R.id.messageImage)
        val caption: TextView = view.findViewById(R.id.messageCaption)
        val videoBadge: TextView = view.findViewById(R.id.videoBadge)
    }

    class AudioVH(view: View) : RecyclerView.ViewHolder(view) {
        val bubble: View = view.findViewById(R.id.audioBubble)
        val play: ImageButton = view.findViewById(R.id.audioPlayButton)
        val wave: ImageView = view.findViewById(R.id.audioWaveImage)
        val transcribe: ImageButton = view.findViewById(R.id.audioTranscribeButton)
        val duration: TextView = view.findViewById(R.id.audioDurationText)
        val caption: TextView = view.findViewById(R.id.audioCaptionText)
        val transcript: TextView = view.findViewById(R.id.audioTranscriptText)
    }

    override fun getItemViewType(position: Int): Int {
        val item = items[position]
        val hasHtml = Regex("<\\s*[a-zA-Z][^>]*>").containsMatchIn(item.text)
        val hasAudio = !item.audioPath.isNullOrBlank() || !item.audioUrl.isNullOrBlank() || !item.ttsText.isNullOrBlank()
        return when {
            item.role == "typing" -> VIEW_TYPING
            item.role == "user" && (!item.imagePath.isNullOrBlank() || !item.videoPath.isNullOrBlank()) -> VIEW_IMAGE_USER
            hasAudio -> VIEW_AUDIO
            item.role == "user" -> VIEW_USER
            hasHtml -> VIEW_HTML
            else -> VIEW_BOT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_USER -> MessageVH(inflater.inflate(R.layout.item_message_user, parent, false))
            VIEW_TYPING -> TypingVH(inflater.inflate(R.layout.item_message_typing, parent, false))
            VIEW_HTML -> HtmlVH(inflater.inflate(R.layout.item_message_html, parent, false))
            VIEW_IMAGE_USER -> ImageUserVH(inflater.inflate(R.layout.item_message_image_user, parent, false))
            VIEW_AUDIO -> AudioVH(inflater.inflate(R.layout.item_message_audio, parent, false))
            else -> MessageVH(inflater.inflate(R.layout.item_message_bot, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is MessageVH -> {
                val item = items[position]
                if (item.role == "user") {
                    holder.text.setBackgroundResource(theme.userBubble)
                    holder.text.setTextColor(theme.userText)
                } else {
                    holder.text.setBackgroundResource(theme.botBubble)
                    holder.text.setTextColor(theme.botText)
                }
                val hasAudio = !item.audioPath.isNullOrBlank() || !item.audioUrl.isNullOrBlank() || !item.ttsText.isNullOrBlank()

                if (hasAudio) {
                    val icon = if (playingMessageTs == item.ts) "⏸" else "▶"
                    RichTextRenderer.bind(holder.text, "$icon ${item.text}")
                    holder.text.setOnClickListener { onMessageClick?.invoke(item) }
                } else {
                    RichTextRenderer.bind(holder.text, item.text)
                    holder.text.setOnClickListener(null)
                }

                holder.text.setOnLongClickListener {
                    val ctx = holder.text.context
                    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("openclaw-message", item.text))
                    Toast.makeText(ctx, ctx.getString(R.string.copied), Toast.LENGTH_SHORT).show()
                    true
                }
            }
            is HtmlVH -> {
                val item = items[position]
                holder.bubble.setBackgroundResource(theme.botBubble)
                holder.label.text = holder.itemView.context.getString(R.string.html_preview_tap)
                holder.hint.text = holder.itemView.context.getString(R.string.html_open_expanded)
                val ws: WebSettings = holder.web.settings
                ws.javaScriptEnabled = false
                ws.domStorageEnabled = false
                ws.allowFileAccess = false
                ws.allowContentAccess = false
                holder.web.loadDataWithBaseURL(
                    null,
                    "<html><body style='margin:0;background:#111827;color:#e5e7eb;font-family:Inter,Arial,sans-serif;font-size:14px;'>${item.text}</body></html>",
                    "text/html",
                    "utf-8",
                    null
                )
                holder.web.setOnTouchListener { _, event ->
                    if (event.action == android.view.MotionEvent.ACTION_UP) {
                        onMessageClick?.invoke(item)
                    }
                    false
                }
                holder.itemView.setOnClickListener { onMessageClick?.invoke(item) }
                holder.hint.setOnClickListener { onMessageClick?.invoke(item) }
            }
            is ImageUserVH -> {
                val item = items[position]
                holder.bubble.setBackgroundResource(theme.userBubble)
                if (!item.imagePath.isNullOrBlank()) {
                    val bmp = BitmapFactory.decodeFile(item.imagePath)
                    if (bmp != null) holder.image.setImageBitmap(bmp)
                    holder.videoBadge.visibility = View.GONE
                } else if (!item.videoPath.isNullOrBlank()) {
                    try {
                        val mmr = MediaMetadataRetriever()
                        mmr.setDataSource(item.videoPath)
                        val frame = mmr.frameAtTime
                        if (frame != null) holder.image.setImageBitmap(frame)
                        mmr.release()
                    } catch (_: Exception) {
                        holder.image.setImageResource(android.R.drawable.ic_media_play)
                    }
                    holder.videoBadge.visibility = View.VISIBLE
                }
                holder.caption.text = item.text
                holder.itemView.setOnClickListener { onMessageClick?.invoke(item) }
            }
            is AudioVH -> {
                val item = items[position]
                if (item.role == "user") {
                    holder.bubble.setBackgroundResource(theme.userBubble)
                    holder.caption.setTextColor(theme.userText)
                } else {
                    holder.bubble.setBackgroundResource(theme.botBubble)
                    holder.caption.setTextColor(theme.botText)
                }

                val playIcon = if (playingMessageTs == item.ts) R.drawable.ic_pause_min else R.drawable.ic_play_min
                holder.play.setImageResource(playIcon)
                holder.play.setColorFilter(if (item.role == "user") theme.userText else theme.botText)
                holder.play.setOnClickListener { onMessageClick?.invoke(item) }
                holder.wave.setColorFilter(theme.statusColor)
                holder.wave.alpha = if (playingMessageTs == item.ts) 1f else 0.85f

                holder.caption.text = if (item.text.isBlank()) "Àudio" else item.text

                val cachedDuration = durationCache[item.ts]
                if (cachedDuration != null) {
                    holder.duration.text = cachedDuration
                } else {
                    holder.duration.text = "--:--"
                    resolveDurationAsync(item)
                }
                holder.duration.setTextColor(theme.statusColor)

                holder.transcribe.visibility = if (showTranscriptionOption) View.VISIBLE else View.GONE
                holder.transcribe.setColorFilter(theme.botText)
                if (!item.transcriptText.isNullOrBlank()) {
                    holder.transcribe.setImageResource(if (item.transcriptVisible) android.R.drawable.ic_menu_close_clear_cancel else android.R.drawable.ic_menu_edit)
                } else {
                    holder.transcribe.setImageResource(android.R.drawable.ic_menu_edit)
                }
                holder.transcribe.setOnClickListener { onAudioTranscribeClick?.invoke(item) }

                if (!item.transcriptText.isNullOrBlank() && item.transcriptVisible) {
                    holder.transcript.visibility = View.VISIBLE
                    holder.transcript.text = item.transcriptText
                } else {
                    holder.transcript.visibility = View.GONE
                }
            }
            is TypingVH -> {
                val bubble = holder.itemView.findViewById<View>(R.id.typingBubble)
                bubble?.setBackgroundResource(theme.botBubble)
                holder.d1.setTextColor(theme.typingDots)
                holder.d2.setTextColor(theme.typingDots)
                holder.d3.setTextColor(theme.typingDots)
                startTypingAnimation(holder)
            }
        }
    }

    private fun resolveDurationAsync(item: ChatMessage) {
        if (durationCache.containsKey(item.ts) || durationLoading.contains(item.ts)) return
        durationLoading.add(item.ts)

        thread {
            val resolved = resolveDuration(item)
            durationCache[item.ts] = resolved
            durationLoading.remove(item.ts)
            val idx = items.indexOfFirst { it.ts == item.ts }
            if (idx >= 0) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    notifyItemChanged(idx)
                }
            }
        }
    }

    private fun resolveDuration(item: ChatMessage): String {
        return try {
            val mmr = MediaMetadataRetriever()
            when {
                !item.audioPath.isNullOrBlank() -> mmr.setDataSource(item.audioPath)
                !item.audioUrl.isNullOrBlank() -> mmr.setDataSource(item.audioUrl, hashMapOf())
                else -> return "0:00"
            }
            val ms = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            mmr.release()
            formatDuration(ms)
        } catch (_: Exception) {
            "0:00"
        }
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = (ms / 1000L).toInt().coerceAtLeast(0)
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format(Locale.getDefault(), "%d:%02d", min, sec)
    }

    private fun startTypingAnimation(holder: TypingVH) {
        animateDot(holder.d1, 0)
        animateDot(holder.d2, 160)
        animateDot(holder.d3, 320)
    }

    private fun animateDot(view: TextView, offset: Long) {
        val anim = AlphaAnimation(0.25f, 1f).apply {
            duration = 450
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
            startOffset = offset
        }
        view.startAnimation(anim)
    }

    override fun getItemCount(): Int = items.size

    fun add(message: ChatMessage) {
        items.add(message)
        notifyItemInserted(items.lastIndex)
    }

    fun replaceLast(message: ChatMessage) {
        if (items.isNotEmpty()) {
            items[items.lastIndex] = message
            notifyItemChanged(items.lastIndex)
        }
    }

    fun setTheme(newTheme: ThemeManager.UiTheme) {
        theme = newTheme
        notifyDataSetChanged()
    }

    fun setPlayingMessage(ts: Long?) {
        playingMessageTs = ts
        notifyDataSetChanged()
    }

    fun setShowTranscriptionOption(enabled: Boolean) {
        showTranscriptionOption = enabled
        notifyDataSetChanged()
    }

    fun setTranscript(ts: Long, text: String, visible: Boolean) {
        val idx = items.indexOfFirst { it.ts == ts }
        if (idx >= 0) {
            val old = items[idx]
            items[idx] = old.copy(transcriptText = text, transcriptVisible = visible)
            notifyItemChanged(idx)
        }
    }

    fun toggleTranscript(ts: Long) {
        val idx = items.indexOfFirst { it.ts == ts }
        if (idx >= 0) {
            val old = items[idx]
            if (!old.transcriptText.isNullOrBlank()) {
                items[idx] = old.copy(transcriptVisible = !old.transcriptVisible)
                notifyItemChanged(idx)
            }
        }
    }
}
