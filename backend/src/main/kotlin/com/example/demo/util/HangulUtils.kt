package com.example.demo.util

object HangulUtils {
    private val CHOSUNG =
            charArrayOf(
                    'ㄱ',
                    'ㄲ',
                    'ㄴ',
                    'ㄷ',
                    'ㄸ',
                    'ㄹ',
                    'ㅁ',
                    'ㅂ',
                    'ㅃ',
                    'ㅅ',
                    'ㅆ',
                    'ㅇ',
                    'ㅈ',
                    'ㅉ',
                    'ㅊ',
                    'ㅋ',
                    'ㅌ',
                    'ㅍ',
                    'ㅎ'
            )

    fun extractChosung(text: String?): String {
        if (text.isNullOrBlank()) return ""

        val sb = StringBuilder()
        for (ch in text) {
            if (ch in '\uAC00'..'\uD7A3') { // Hangul Syllables
                val chosungIndex = (ch.code - 0xAC00) / 28 / 21
                sb.append(CHOSUNG[chosungIndex])
            } else {
                sb.append(ch) // Keep non-Hangul characters as is
            }
        }
        return sb.toString()
    }
}
