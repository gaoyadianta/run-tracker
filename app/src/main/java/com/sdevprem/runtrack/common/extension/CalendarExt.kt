package com.sdevprem.runtrack.common.extension

import java.util.Calendar

fun Calendar.setDateToWeekFirstDay() = apply {
    // Keep RunTrack's statistics weeks stable across device locales. Calendar's
    // DAY_OF_WEEK setter uses firstDayOfWeek, which differs by locale and can
    // otherwise move a Sunday into the following week.
    add(Calendar.DAY_OF_MONTH, -(get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY))
    setMinimumTime()
}

fun Calendar.setDateToWeekLastDay() = apply {
    add(Calendar.DAY_OF_MONTH, Calendar.SATURDAY - get(Calendar.DAY_OF_WEEK))
    setMaximumTime()
}

fun Calendar.setMinimumTime() = apply {
    set(Calendar.HOUR_OF_DAY, getActualMinimum(Calendar.HOUR_OF_DAY))
    set(Calendar.MINUTE, getActualMinimum(Calendar.MINUTE))
    set(Calendar.SECOND, getActualMinimum(Calendar.SECOND))
    set(Calendar.MILLISECOND, getActualMinimum(Calendar.MILLISECOND))
}

fun Calendar.setMaximumTime() = apply {
    set(Calendar.HOUR_OF_DAY, getActualMaximum(Calendar.HOUR_OF_DAY))
    set(Calendar.MINUTE, getActualMaximum(Calendar.MINUTE))
    set(Calendar.SECOND, getActualMaximum(Calendar.SECOND))
    set(Calendar.MILLISECOND, getActualMaximum(Calendar.MILLISECOND))
}

operator fun ClosedRange<Calendar>.iterator() = object : Iterator<Calendar> {
    var current = start

    override fun hasNext(): Boolean = current <= endInclusive

    override fun next(): Calendar {
        val temp = current
        current = (current.clone() as Calendar).apply {
            add(Calendar.DAY_OF_WEEK, 1)
        }
        return temp
    }
}

fun ClosedRange<Calendar>.toList(): List<Calendar> = buildList {
    for (c in this@toList) add(c)
}
