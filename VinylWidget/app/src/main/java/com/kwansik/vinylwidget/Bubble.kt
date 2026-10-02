package com.kwansik.vinylwidget

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.min

/**
 * 캐릭터 디자인의 말풍선: 테두리 없는 반투명 흰색 + 부드러운 그림자 + 곡선 꼬리 (메신저 말풍선 문법).
 * 글자는 띄어쓰기 단위로 줄바꿈 (단어 중간에서 끊지 않음)
 */
object Bubble {

    /** maxW 안에 들어가게 띄어쓰기 단위로 나눔 (최대 maxLines줄, 넘치면 마지막 줄에 …) */
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

    /**
     * (left, top)에 말풍선을 그림. tailRight = 꼬리가 오른쪽(위쪽 가까이)을, 아니면 왼쪽(가운데)을 가리킴.
     * 그린 상자를 돌려줌
     */
    fun draw(c: Canvas, left: Float, top: Float, lines: List<String>, textP: Paint, tailRight: Boolean): RectF {
        val fs = textP.textSize
        val padH = fs * 0.85f; val padV = fs * 0.55f; val lineH = fs * 1.3f
        val w = (lines.maxOfOrNull { textP.measureText(it) } ?: 0f) + padH * 2
        val h = lines.size * lineH + padV * 2 - (lineH - fs) * 0.5f
        val box = RectF(left, top, left + w, top + h)
        val r = min(fs * 1.1f, h / 2)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xF2FFFFFF.toInt()
            setShadowLayer(fs * 0.6f, 0f, fs * 0.15f, 0x2E000000)
        }
        // 꼬리 (10×10 단위 곡선 모양을 글자 크기에 맞춰 키움)
        val u = fs * 0.07f
        val tail = Path()
        if (tailRight) {
            val x = box.right - u * 3; val y = box.top + fs * 0.45f
            tail.moveTo(x, y); tail.quadTo(x + 4 * u, y + 4 * u, x + 10 * u, y + 2 * u); tail.quadTo(x + 5 * u, y + 7 * u, x, y + 10 * u); tail.close()
        } else {
            val x = box.left + u * 3; val y = box.centerY() - 5 * u
            tail.moveTo(x, y); tail.quadTo(x - 4 * u, y + 4 * u, x - 10 * u, y + 2 * u); tail.quadTo(x - 5 * u, y + 7 * u, x, y + 10 * u); tail.close()
        }
        c.drawPath(tail, fill)
        c.drawRoundRect(box, r, r, fill)
        // 글자
        textP.textAlign = Paint.Align.LEFT
        val fm = textP.fontMetrics
        lines.forEachIndexed { i, s ->
            val cy = box.top + padV + lineH * i + fs * 0.5f
            c.drawText(s, box.left + padH, cy - (fm.ascent + fm.descent) / 2, textP)
        }
        return box
    }
}
