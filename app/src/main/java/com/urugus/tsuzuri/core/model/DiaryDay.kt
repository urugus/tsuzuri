package com.urugus.tsuzuri.core.model

import java.time.LocalDate

/**
 * 1日分の日記（vault の `YYYY-MM-DD.md` 1ファイルに対応）。
 *
 * [events] はその日に属する出来事の集合。表示・保存順は codec 側で
 * 時刻→作成時刻→id の安定ソートに正規化する。
 */
data class DiaryDay(
    val date: LocalDate,
    val events: List<Event>,
)
