package com.cashflow.forecast;

import com.cashflow.data.Models.FlowType;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class ForecastDtos {
    private ForecastDtos() {}
    public record Event(String id,LocalDate date,FlowType type,BigDecimal amount,String description,String origin) {}
    public record DailyPoint(LocalDate date,BigDecimal predictedUnplannedNet,BigDecimal scheduledInflows,BigDecimal scheduledOutflows,BigDecimal closingBalance) {}
    public record Summary(BigDecimal minimumBalance,LocalDate minimumDate,LocalDate reserveBreachDate,BigDecimal finalBalance) {}
    public record ForecastView(UUID id,LocalDate startDate,LocalDate endDate,BigDecimal openingBalance,BigDecimal reserve,
        String modelName,String modelVersion,Instant generatedAt,List<String> limitations,
        List<Event> events,List<DailyPoint> points,Summary summary,boolean stale) {}
    public record History(LocalDate date,BigDecimal netFlow) {}
    public record Prediction(List<History> predictions,String modelName,String modelVersion,List<String> limitations) {}
}
