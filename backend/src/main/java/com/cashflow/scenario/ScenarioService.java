package com.cashflow.scenario;

import com.cashflow.common.ApiException;
import com.cashflow.data.Models.*;
import com.cashflow.data.Repositories.*;
import com.cashflow.finance.*;
import com.cashflow.finance.FinanceDtos.PageView;
import com.cashflow.forecast.*;
import com.cashflow.forecast.ForecastDtos.*;
import com.cashflow.scenario.ScenarioDtos.*;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional
public class ScenarioService {
    private final FinanceService finance; private final ForecastService forecasts;
    private final Scenarios scenarios; private final Changes changes; private final ScenarioPoints points;
    public ScenarioService(FinanceService finance,ForecastService forecasts,Scenarios scenarios,Changes changes,ScenarioPoints points) {
        this.finance=finance; this.forecasts=forecasts; this.scenarios=scenarios; this.changes=changes; this.points=points;
    }
    public Scenario owned(UUID id) { return scenarios.findByIdAndBusinessId(id,finance.current().id).orElseThrow(ApiException::notFound); }
    public ScenarioView view(Scenario s) { return new ScenarioView(s.id,s.name,s.description,s.baselineId,changes.findByScenarioIdOrderByPosition(s.id).stream().map(c->new ChangeInput(c.kind,c.targetEventId,c.newDate,c.amount,c.type,c.description)).toList(),s.createdAt,s.simulatedAt); }
    public PageView<ScenarioView> list(int page) { ApiException.require(page>=0,"Invalid page."); return PageView.of(scenarios.findByBusinessId(finance.current().id,PageRequest.of(page,20,Sort.by("createdAt").descending())).map(this::view)); }
    public ScenarioView save(UUID id,ScenarioInput d) {
        var base=forecasts.view(forecasts.owned(d.baselineId()));
        ReplayEngine.apply(base.events(),base.startDate(),d.changes());
        var s=id==null?new Scenario():owned(id);
        s.businessId=finance.current().id; s.baselineId=d.baselineId(); s.name=d.name().trim(); s.description=d.description()==null?"":d.description(); s.simulatedAt=null;
        scenarios.saveAndFlush(s);
        changes.deleteByScenarioId(s.id); changes.flush(); points.deleteByScenarioId(s.id); points.flush();
        int n=0;
        for(var input:d.changes()) {
            var c=new Change(); c.scenarioId=s.id; c.position=n++; c.kind=input.kind(); c.targetEventId=input.targetEventId(); c.newDate=input.newDate(); c.amount=input.amount(); c.type=input.type(); c.description=input.description(); changes.save(c);
        }
        changes.flush(); return view(s);
    }
    public Comparison simulate(UUID id) {
        var s=owned(id); var base=forecasts.view(forecasts.owned(s.baselineId));
        var events=ReplayEngine.apply(base.events(),base.startDate(),view(s).changes());
        var estimates=base.points().stream().map(p->new History(p.date(),p.predictedUnplannedNet())).toList();
        var daily=FinancialEngine.calculate(base.startDate(),base.openingBalance(),estimates,events);
        points.deleteByScenarioId(s.id); points.flush();
        for(var p:daily) { var row=new ScenarioPoint(); row.scenarioId=s.id; ForecastService.copy(p,row); points.save(row); }
        s.simulatedAt=Instant.now(); points.flush(); return compare(id);
    }
    public Comparison compare(UUID id) {
        var s=owned(id); if(s.simulatedAt==null) throw new ApiException(409,"Run this scenario before comparing.");
        var base=forecasts.view(forecasts.owned(s.baselineId));
        var daily=points.findByScenarioIdOrderByDate(s.id).stream().map(ForecastService::point).toList();
        var summary=FinancialEngine.summarize(daily,base.reserve()); var deltas=new ArrayList<Delta>();
        for(int i=0;i<daily.size();i++) deltas.add(new Delta(daily.get(i).date(),daily.get(i).closingBalance().subtract(base.points().get(i).closingBalance())));
        var minimumChange=summary.minimumBalance().subtract(base.summary().minimumBalance());
        var finalChange=summary.finalBalance().subtract(base.summary().finalBalance());
        boolean newer=!forecasts.latest().id().equals(base.id());
        String explanation="Minimum projected cash changes by INR "+minimumChange.toPlainString()+". The closing balance on "+base.endDate()+" changes by INR "+finalChange.toPlainString()+". "
            +(summary.reserveBreachDate()==null?"No projected reserve breach in this scenario.":"The first projected reserve breach is "+summary.reserveBreachDate()+".")
            +" Changes outside the 30-day horizon are excluded. The original financial records are unchanged.";
        return new Comparison(view(s),base,daily,summary,deltas,minimumChange,finalChange,newer,explanation);
    }
    public void delete(UUID id) { scenarios.delete(owned(id)); }
}
