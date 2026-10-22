package com.cashflow.forecast;

import com.cashflow.forecast.ForecastDtos.*;
import com.cashflow.data.Models.FlowType;
import java.math.*;
import java.time.LocalDate;
import java.util.*;

public final class FinancialEngine {
    private FinancialEngine() {}
    public static List<DailyPoint> calculate(LocalDate start,BigDecimal opening,List<History> predictions,List<Event> events) {
        var result=new ArrayList<DailyPoint>(); BigDecimal balance=opening;
        for(int i=0;i<30;i++) {
            LocalDate day=start.plusDays(i); BigDecimal in=BigDecimal.ZERO,out=BigDecimal.ZERO;
            for(Event e:events) if(e.date().equals(day)) {
                if(e.type()==FlowType.INFLOW) in=in.add(e.amount()); else out=out.add(e.amount());
            }
            BigDecimal estimate=predictions.get(i).netFlow().setScale(2,RoundingMode.HALF_UP);
            balance=balance.add(estimate).add(in).subtract(out).setScale(2,RoundingMode.HALF_UP);
            result.add(new DailyPoint(day,estimate,in,out,balance));
        }
        return List.copyOf(result);
    }
    public static Summary summarize(List<DailyPoint> points,BigDecimal reserve) {
        DailyPoint min=points.stream().min(Comparator.comparing(DailyPoint::closingBalance)).orElseThrow();
        LocalDate breach=points.stream().filter(p->p.closingBalance().compareTo(reserve)<0).map(DailyPoint::date).findFirst().orElse(null);
        return new Summary(min.closingBalance(),min.date(),breach,points.getLast().closingBalance());
    }
    public static Prediction fallback(List<History> history,LocalDate start,String reason) {
        var values=history.subList(Math.max(0,history.size()-56),history.size()); var result=new ArrayList<History>();
        boolean insufficient=values.size()<28;
        for(int i=0;i<30;i++) {
            LocalDate day=start.plusDays(i);
            List<BigDecimal> same=values.stream().filter(h->h.date().getDayOfWeek()==day.getDayOfWeek()).map(History::netFlow).toList();
            BigDecimal net=insufficient||same.isEmpty()?BigDecimal.ZERO:same.stream().reduce(BigDecimal.ZERO,BigDecimal::add).divide(BigDecimal.valueOf(same.size()),2,RoundingMode.HALF_UP);
            result.add(new History(day,net));
        }
        return new Prediction(result,insufficient?"Scheduled-only baseline":"Historical weekday baseline","java-baseline-v1",
            List.of(reason,insufficient?"Fewer than 28 days of history: unplanned movement is assumed zero.":"Uses the last 56 days of explicitly unplanned settled movements; missing days are treated as zero."));
    }
}
