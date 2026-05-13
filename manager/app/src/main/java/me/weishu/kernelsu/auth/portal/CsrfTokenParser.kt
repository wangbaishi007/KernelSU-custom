package me.weishu.kernelsu.auth.portal

import java.util.regex.Pattern

/**
 * Parse csrf_token from login HTML (regex only; same patterns as oss-uploader CsrfTokenParser fallback).
 */
object CsrfTokenParser {

    private val regexFallback =
        Pattern.compile(
            "<input[^>]+name=[\"']csrf_token[\"'][^>]+value=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE,
        )

    private val regexAlt =
        Pattern.compile(
            "value=[\"']([^\"']+)[\"'][^>]*name=[\"']csrf_token[\"']",
            Pattern.CASE_INSENSITIVE,
        )

    fun parse(html: String?): String? {
        if (html.isNullOrEmpty()) return null
        regexFallback.matcher(html).let { m ->
            if (m.find()) return m.group(1)
        }
        regexAlt.matcher(html).let { m ->
            if (m.find()) return m.group(1)
        }
        return null
    }
}
