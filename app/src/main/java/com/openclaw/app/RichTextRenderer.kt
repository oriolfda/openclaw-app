package com.openclaw.app

import android.graphics.Typeface
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.core.text.HtmlCompat

object RichTextRenderer {

    // Supports both:
    // ```python\n...\n```
    // ```python ... ```
    private val codeFenceRegex = Regex("```([a-zA-Z0-9_+-]*)\\s*([\\s\\S]*?)```")

    fun bind(textView: TextView, raw: String) {
        val html = toSafeHtml(raw)
        val spanned: Spanned = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
        textView.text = spanned
        textView.movementMethod = LinkMovementMethod.getInstance()

        if (looksLikeCode(raw)) {
            textView.typeface = Typeface.MONOSPACE
            textView.textSize = 14f
            textView.setTextIsSelectable(true)
            textView.setHorizontallyScrolling(true)
        } else {
            textView.typeface = Typeface.DEFAULT
            textView.textSize = 16f
            textView.setTextIsSelectable(true)
            textView.setHorizontallyScrolling(false)
        }
    }

    private fun toSafeHtml(raw: String): String {
        val noDanger = raw
            .replace(Regex("<\\s*script[\\s\\S]*?<\\s*/\\s*script\\s*>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<\\s*style[\\s\\S]*?<\\s*/\\s*style\\s*>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("on[a-zA-Z]+\\s*=\\s*\"[^\"]*\"", RegexOption.IGNORE_CASE), "")
            .replace(Regex("on[a-zA-Z]+\\s*=\\s*'[^']*'", RegexOption.IGNORE_CASE), "")
            .replace(Regex("javascript:", RegexOption.IGNORE_CASE), "")
            .trim()

        // Preserve fenced code blocks as <pre><code>.
        val blocks = mutableListOf<String>()
        val hadCodeFences = codeFenceRegex.containsMatchIn(noDanger)
        val withPlaceholders = codeFenceRegex.replace(noDanger) { m ->
            val lang = m.groupValues[1].trim()
            val code = escapeHtml(m.groupValues[2].trim())
            val block = "<pre><code data-lang=\"$lang\">$code</code></pre>"
            blocks.add(block)
            "@@CODE_BLOCK_${blocks.lastIndex}@@"
        }

        val hasHtmlTags = Regex("<\\s*[a-zA-Z][^>]*>").containsMatchIn(withPlaceholders)

        var txt = if (hasHtmlTags) {
            // Keep user HTML tags (already sanitized above).
            withPlaceholders
        } else {
            // Plain text/markdown path.
            escapeHtml(withPlaceholders)
        }

        // Minimal markdown support (applied mostly for non-HTML input)
        txt = txt
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "<b>$1</b>")
            .replace(Regex("(^|\\s)_(.+?)_($|\\s)"), "$1<i>$2</i>$3")
            .replace(Regex("`([^`]+)`"), "<code>$1</code>")
            .replace(Regex("\\[(.+?)\\]\\((https?://[^)]+)\\)"), "<a href=\"$2\">$1</a>")

        // Restore code blocks
        blocks.forEachIndexed { i, block ->
            txt = txt.replace("@@CODE_BLOCK_$i@@", block)
        }

        // If text looks like raw code and there were no explicit fences,
        // force <pre><code> to preserve spacing.
        if (!hasHtmlTags && !hadCodeFences && looksLikeCode(noDanger)) {
            return "<pre><code>${escapeHtml(noDanger)}</code></pre>"
        }

        // Line breaks
        txt = txt.replace("\n", "<br>")

        return txt
    }

    private fun looksLikeCode(s: String): Boolean {
        val t = s.trim()
        if (t.contains("```") || t.contains("<pre") || t.contains("<code")) return true
        val lines = t.lines()
        if (lines.size < 3) return false
        var score = 0
        val markers = listOf("def ", "class ", "return ", "{", "}", "</", "<div", "if ", "for ", "while ", ";", "=>")
        for (m in markers) if (t.contains(m)) score++
        return score >= 3
    }

    private fun escapeHtml(s: String): String {
        return s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
