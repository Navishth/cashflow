package com.cashflow.finance;

import com.cashflow.data.Models.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

public final class Recurrence {
    private Recurrence() {}
    public static List<LocalDate> dates(Rule r,LocalDate from,LocalDate to) {
        if(!r.active || from.isAfter(to)) return List.of();
        LocalDate limit=r.endDate==null || r.endDate.isAfter(to)?to:r.endDate;
        long step=r.frequency==Frequency.WEEKLY
            ? Math.max(0,ChronoUnit.WEEKS.between(r.startDate,from))
            : Math.max(0,ChronoUnit.MONTHS.between(YearMonth.from(r.startDate),YearMonth.from(from)));
        var result=new ArrayList<LocalDate>();
        for(int guard=0;guard<10000;guard++,step++) {
            // Always advance the original anchor, never the clamped previous occurrence.
            LocalDate day=r.frequency==Frequency.WEEKLY?r.startDate.plusWeeks(step):r.startDate.plusMonths(step);
            if(day.isAfter(limit)) break;
            if(!day.isBefore(from)) result.add(day);
        }
        return result;
    }
    public static boolean isOccurrence(Rule r,LocalDate date) {
        boolean active=r.active;
        try { r.active=true; return dates(r,date,date).contains(date); }
        finally { r.active=active; }
    }
}
