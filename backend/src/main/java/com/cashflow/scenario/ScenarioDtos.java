package com.cashflow.scenario;
import com.cashflow.data.Models.*;
import com.cashflow.forecast.ForecastDtos.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class ScenarioDtos {
    private ScenarioDtos() {}
    public record ChangeInput(@NotNull ChangeKind kind,@Size(max=100) String targetEventId,LocalDate newDate,
        @DecimalMin("0.01") @Digits(integer=12,fraction=2) BigDecimal amount,FlowType type,@Size(max=200) String description) {}
    public record ScenarioInput(@NotBlank @Size(max=120) String name,@Size(max=500) String description,
        @NotNull UUID baselineId,@NotEmpty @Size(max=20) List<@Valid ChangeInput> changes) {}
    public record ScenarioView(UUID id,String name,String description,UUID baselineId,List<ChangeInput> changes,Instant createdAt,Instant simulatedAt) {}
    public record Delta(LocalDate date,BigDecimal difference) {}
    public record Comparison(ScenarioView scenario,ForecastView baseline,List<DailyPoint> points,Summary summary,
        List<Delta> deltas,BigDecimal minimumBalanceChange,BigDecimal finalBalanceChange,boolean newerBaselineAvailable,String explanation) {}
}
