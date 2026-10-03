package com.kwansik.vinylwidget

import android.graphics.Paint

/** 글자를 띄어쓰기 단위로 줄바꿈 (단어 중간에서 끊지 않음). 줄 수를 넘으면 마지막 줄 끝을 …으로 */
object TextWrap {
    fun wrap(text: String, p: Paint, maxW: Float, maxLines: Int): List<String> {
        val lines = mutableListOf<String>()
        var cur = ""
        for (w in text.split(" ")) {
            val next = if (cur.isEmpty()) w else "$cur $w"
            if (p.measureText(next) <= maxW || cur.isEmpty()) cur = next
            else { lines += cur; cur = w }
        }
        if (cur.isNotEmpty()) lines += cur
        if (lines.size <= maxLines) return lines
        val kept = lines.take(maxLines).toMutableList()
        var last = kept.last() + "…"
        while (last.length > 1 && p.measureText(last) > maxW) last = last.dropLast(2) + "…"
        kept[kept.size - 1] = last
        return kept
    }
}
