package com.cashflow.forecast;

import com.cashflow.common.ApiException;
import com.cashflow.data.Models.*;
import com.cashflow.data.Repositories.*;
import com.cashflow.finance.*;
import com.cashflow.forecast.ForecastDtos.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service @Transactional
public class ForecastService {
    private final FinanceService finance; private final Flows flows; private final Rules rules;
    private final Forecasts forecasts; private final ForecastPoints points; private final MlClient ml; private final ObjectMapper json;
    public ForecastService(FinanceService finance,Flows flows,Rules rules,Forecasts forecasts,ForecastPoints points,MlClient ml,ObjectMapper json) {
        this.finance=finance; this.flows=flows; this.rules=rules; this.forecasts=forecasts; this.points=points; this.ml=ml; this.json=json;
    }
    public Forecast owned(UUID id) { return forecasts.findByIdAndBusinessId(id,finance.current().id).orElseThrow(ApiException::notFound); }
    public ForecastView latest() { return view(forecasts.findFirstByBusinessIdOrderByCreatedAtDesc(finance.current().id).orElseThrow(ApiException::notFound)); }
    @Transactional(isolation=Isolation.REPEATABLE_READ)
    public ForecastView generate() {
        var b=finance.current(); var entries=flows.findByBusinessId(b.id); LocalDate today=finance.today(b),start=today.plusDays(1),end=start.plusDays(29);
        List<Event> events=new ArrayList<>();
        entries.stream().filter(f->f.status==FlowStatus.PLANNED&&!f.expectedDate.isBefore(start)&&!f.expectedDate.isAfter(end))
            .forEach(f->events.add(new Event(f.id.toString(),f.expectedDate,f.type,f.amount,f.description,"PLANNED")));
        Set<String> replacements=new HashSet<>();
        entries.stream().filter(f->f.recurringRuleId!=null).forEach(f->replacements.add(f.recurringRuleId+":"+f.occurrenceDate));
        for(var rule:rules.findByBusinessIdOrderByStartDate(b.id)) for(var date:Recurrence.dates(rule,start,end)) {
            String key=rule.id+":"+date;
            if(!replacements.contains(key)) events.add(new Event(key,date,rule.type,rule.amount,rule.description,"RECURRING"));
        }
        events.sort(Comparator.comparing(Event::date).thenComparing(Event::id));
        LocalDate historyStart=b.openingDate.isAfter(today.minusDays(364))?b.openingDate:today.minusDays(364);
        var history=new ArrayList<History>(); Map<LocalDate,BigDecimal> totals=new HashMap<>();
        entries.stream().filter(FinanceService::settled).filter(f->f.source==Source.UNPLANNED)
            .forEach(f->totals.merge(f.expectedDate,FinanceService.signed(f.type,f.amount),BigDecimal::add));
        for(LocalDate d=historyStart;!d.isAfter(today);d=d.plusDays(1)) history.add(new History(d,totals.getOrDefault(d,BigDecimal.ZERO)));
        var prediction=ml.predict(history,start,b.demo);
        var limitations=new ArrayList<>(prediction.limitations());
        limitations.add("Estimates depend on complete records. Missing historical days count as zero. Predictions are not guaranteed.");
        long overdue=entries.stream().filter(f->f.status==FlowStatus.PLANNED&&!f.expectedDate.isAfter(today)).count();
        if(overdue>0) limitations.add(overdue+" overdue planned transaction(s) were excluded. Update their expected dates or mark them settled.");
        var f=new Forecast(); f.businessId=b.id; f.startDate=start; f.openingBalance=finance.balance(b,entries); f.reserve=b.reserve;
        f.inputRevision=b.revision; f.modelName=prediction.modelName(); f.modelVersion=prediction.modelVersion(); f.events=encode(events); f.limitations=encode(limitations);
        forecasts.saveAndFlush(f);
        for(var p:FinancialEngine.calculate(start,f.openingBalance,prediction.predictions(),events)) {
            var row=new ForecastPoint(); row.forecastId=f.id; copy(p,row); points.save(row);
        }
        points.flush(); return view(f);
    }
    public ForecastView view(Forecast f) {
        List<DailyPoint> daily=points.findByForecastIdOrderByDate(f.id).stream().map(ForecastService::point).toList();
        List<Event> events=decode(f.events,new TypeReference<>(){}); List<String> limitations=decode(f.limitations,new TypeReference<>(){});
        var b=finance.current();
        return new ForecastView(f.id,f.startDate,f.startDate.plusDays(29),f.openingBalance,f.reserve,f.modelName,f.modelVersion,f.createdAt,
            limitations,events,daily,FinancialEngine.summarize(daily,f.reserve),f.inputRevision!=b.revision||!f.startDate.equals(finance.today(b).plusDays(1)));
    }
    public static DailyPoint point(Point p) { return new DailyPoint(p.date,p.predictedUnplannedNet,p.scheduledInflows,p.scheduledOutflows,p.closingBalance); }
    public static void copy(DailyPoint p,Point row) { row.date=p.date(); row.predictedUnplannedNet=p.predictedUnplannedNet(); row.scheduledInflows=p.scheduledInflows(); row.scheduledOutflows=p.scheduledOutflows(); row.closingBalance=p.closingBalance(); }
    private String encode(Object value) { try { return json.writeValueAsString(value); } catch(Exception e) { throw new IllegalStateException(e); } }
    private <T> T decode(String value,TypeReference<T> type) { try { return json.readValue(value,type); } catch(Exception e) { throw new IllegalStateException(e); } }
}
