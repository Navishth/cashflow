package com.cashflow;

import com.cashflow.common.ApiException;
import com.cashflow.data.Models.*;
import com.cashflow.finance.Recurrence;
import com.cashflow.forecast.*;
import com.cashflow.forecast.ForecastDtos.*;
import com.cashflow.scenario.*;
import com.cashflow.scenario.ScenarioDtos.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FinancialEngineTest {
    final LocalDate start=LocalDate.of(2026,1,31);
    List<History> zeros() { return java.util.stream.IntStream.range(0,30).mapToObj(i->new History(start.plusDays(i),BigDecimal.ZERO)).toList(); }
    @Test void exactDecimalArithmeticAndStrictReserveBoundary() {
        var events=List.of(new Event("a",start,FlowType.INFLOW,new BigDecimal("0.10"),"Receipt","PLANNED"),
            new Event("b",start,FlowType.INFLOW,new BigDecimal("0.20"),"Receipt","PLANNED"),
            new Event("c",start.plusDays(1),FlowType.OUTFLOW,new BigDecimal("0.01"),"Fee","PLANNED"));
        var points=FinancialEngine.calculate(start,new BigDecimal("100.00"),zeros(),events);
        assertThat(points.getFirst().closingBalance()).isEqualByComparingTo("100.30");
        var summary=FinancialEngine.summarize(points,new BigDecimal("100.30"));
        assertThat(summary.reserveBreachDate()).isEqualTo(start.plusDays(1));
        assertThat(summary.minimumDate()).isEqualTo(start.plusDays(1));
    }
    @Test void monthEndAnchorsRecoverAfterFebruaryAndLeapYear() {
        var r=new Rule(); r.startDate=LocalDate.of(2024,1,31); r.frequency=Frequency.MONTHLY; r.active=true;
        assertThat(Recurrence.dates(r,r.startDate,LocalDate.of(2024,4,30))).containsExactly(LocalDate.of(2024,1,31),LocalDate.of(2024,2,29),LocalDate.of(2024,3,31),LocalDate.of(2024,4,30));
        r.active=false; assertThat(Recurrence.dates(r,r.startDate,r.startDate.plusYears(1))).isEmpty();
    }
    @Test void weeklyRulesRespectInclusiveEndAndRepeatDeterministically() {
        var r=new Rule(); r.startDate=start; r.frequency=Frequency.WEEKLY; r.active=true; r.endDate=start.plusWeeks(2);
        var dates=Recurrence.dates(r,start.plusDays(1),start.plusMonths(3));
        assertThat(dates).containsExactly(start.plusWeeks(1),start.plusWeeks(2));
        assertThat(Recurrence.dates(r,start.plusDays(1),start.plusMonths(3))).isEqualTo(dates);
    }
    @Test void replayCopiesBaselineAndSupportsEveryChange() {
        var original=List.of(new Event("receipt",start,FlowType.INFLOW,new BigDecimal("100"),"Receipt","PLANNED"),
            new Event("cost",start.plusDays(2),FlowType.OUTFLOW,new BigDecimal("40"),"Cost","PLANNED"));
        var changes=List.of(new ChangeInput(ChangeKind.RESCHEDULE,"receipt",start.plusDays(35),null,null,null),
            new ChangeInput(ChangeKind.AMOUNT_CHANGE,"cost",null,new BigDecimal("60"),null,null),
            new ChangeInput(ChangeKind.ADD_CASH_FLOW,null,start,new BigDecimal("25"),FlowType.INFLOW,"New sale"));
        var altered=ReplayEngine.apply(original,start,changes);
        assertThat(original.getFirst().date()).isEqualTo(start);
        assertThat(FinancialEngine.calculate(start,BigDecimal.ZERO,zeros(),altered).getLast().closingBalance()).isEqualByComparingTo("-35");
        assertThat(ReplayEngine.apply(original,start,changes)).isEqualTo(altered);
        assertThat(ReplayEngine.apply(original,start,List.of(new ChangeInput(ChangeKind.REMOVE_CASH_FLOW,"cost",null,null,null,null)))).hasSize(1);
        assertThatThrownBy(()->ReplayEngine.apply(original,start,List.of(new ChangeInput(ChangeKind.REMOVE_CASH_FLOW,"foreign-id",null,null,null,null)))).isInstanceOf(ApiException.class);
    }
    @Test void insufficientHistoryIsAnExplicitZeroBaseline() {
        var p=FinancialEngine.fallback(List.of(),start,"No history");
        assertThat(p.modelName()).isEqualTo("Scheduled-only baseline");
        assertThat(p.predictions()).hasSize(30).allSatisfy(day->assertThat(day.netFlow()).isEqualByComparingTo("0"));
    }
    @Test void unavailableInferenceSafelyFallsBack() {
        var history=java.util.stream.IntStream.range(0,56).mapToObj(i->new History(start.minusDays(56-i),new BigDecimal("123.45"))).toList();
        var p=new MlClient("http://127.0.0.1:1","test-internal-key-with-32-characters").predict(history,start,false);
        assertThat(p.modelVersion()).isEqualTo("java-baseline-v1");
        assertThat(p.predictions().getFirst().netFlow()).isEqualByComparingTo("123.45");
    }
}
