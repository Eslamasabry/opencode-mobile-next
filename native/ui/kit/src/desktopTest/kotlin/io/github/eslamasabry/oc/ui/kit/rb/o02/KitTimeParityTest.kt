package io.github.eslamasabry.oc.ui.kit.rb.o02

import kotlin.test.*
import io.github.eslamasabry.oc.ui.kit.text.KitTime
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class KitTimeParityTest {
    private val now = LocalDateTime.of(2026,9,28,23,40)
    private fun clock(at: LocalDateTime, use24h: Boolean) = at.format(DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm a",Locale.US))
    private fun moment(at: LocalDateTime, use24h: Boolean = false) = KitTime.momentWith(at,now,use24h,{it},
        {a,b -> a.toLocalDate()==b.toLocalDate()}, {a,b -> a.year==b.year}, ::clock,
        {it.format(DateTimeFormatter.ofPattern("MMM d",Locale.US))}, {it.format(DateTimeFormatter.ofPattern("MMM d, yyyy",Locale.US))})
    @Test fun followsClockSettingAndUsesShortestLocalDate() {
        val at=now.withHour(23).withMinute(19)
        assertEquals("11:19 PM",KitTime.clockWith(at,false,{it},::clock))
        assertEquals("23:19",KitTime.clockWith(at,true,{it},::clock))
        assertEquals("9:05 AM",moment(now.withHour(9).withMinute(5)))
        assertEquals("Sep 25, 8:19 PM",moment(LocalDateTime.of(2026,9,25,20,19)))
        assertEquals("Dec 31, 2025, 8:00 AM",moment(LocalDateTime.of(2025,12,31,8,0)))
    }
    @Test fun localizesBeforeComparisonAndFormatting() {
        val utc=LocalDateTime.of(2026,9,28,23,30)
        val result=KitTime.momentWith(utc,utc.plusMinutes(15),true,{it.plusHours(2)},
            {a,b -> a.toLocalDate()==b.toLocalDate()}, {a,b -> a.year==b.year}, ::clock,{error("same local day")},{error("same local year")})
        assertEquals("01:30",result)
    }
}
