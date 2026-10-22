package com.cashflow.scenario;
import com.cashflow.common.ApiException;
import com.cashflow.data.Models.ChangeKind;
import com.cashflow.forecast.ForecastDtos.*;
import com.cashflow.scenario.ScenarioDtos.ChangeInput;
import java.time.LocalDate;
import java.util.*;
import static com.cashflow.common.ApiException.require;

public final class ReplayEngine {
    private ReplayEngine() {}
    public static List<Event> apply(List<Event> original,LocalDate start,List<ChangeInput> changes) {
        var events=new LinkedHashMap<String,Event>(); original.forEach(e->events.put(e.id(),e));
        int counter=0;
        for(var c:changes) {
            if(c.kind()==ChangeKind.ADD_CASH_FLOW || c.kind()==ChangeKind.RESCHEDULE)
                require(c.newDate()!=null&&!c.newDate().isBefore(start)&&!c.newDate().isAfter(start.plusDays(365)),"Scenario date must be on or after the baseline start and within one year.");
            if(c.kind()==ChangeKind.ADD_CASH_FLOW || c.kind()==ChangeKind.AMOUNT_CHANGE)
                require(c.amount()!=null&&c.amount().signum()>0,"Enter a positive replacement amount.");
            if(c.kind()==ChangeKind.ADD_CASH_FLOW) {
                require(c.type()!=null,"Choose inflow or outflow.");
                String key="hypothetical:"+(counter++);
                events.put(key,new Event(key,c.newDate(),c.type(),c.amount(),c.description()==null?"Hypothetical transaction":c.description(),"HYPOTHETICAL")); continue;
            }
            var target=events.get(c.targetEventId());
            if(target==null) throw new ApiException(400,"Selected transaction is not in this baseline, or was removed by an earlier change.");
            switch(c.kind()) {
                case REMOVE_CASH_FLOW -> events.remove(target.id());
                case RESCHEDULE -> events.put(target.id(),new Event(target.id(),c.newDate(),target.type(),target.amount(),target.description(),target.origin()));
                case AMOUNT_CHANGE -> events.put(target.id(),new Event(target.id(),target.date(),target.type(),c.amount(),target.description(),target.origin()));
                default -> throw new ApiException(400,"Unsupported change.");
            }
        }
        return List.copyOf(events.values());
    }
}
