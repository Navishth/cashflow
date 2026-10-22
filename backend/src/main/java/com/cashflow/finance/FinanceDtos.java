package com.cashflow.finance;

import com.cashflow.data.Models.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.Page;

public final class FinanceDtos {
    private FinanceDtos() {}
    public record BusinessInput(@NotBlank @Size(max=120) String name,@NotBlank String currency,
        @NotBlank @Size(max=80) String timezone,@NotNull LocalDate openingDate,
        @NotNull @Digits(integer=12,fraction=2) BigDecimal openingBalance,
        @NotNull @DecimalMin("0") @Digits(integer=12,fraction=2) BigDecimal reserve,boolean demo) {}
    public record BusinessView(UUID id,String name,String currency,String timezone,LocalDate openingDate,BigDecimal openingBalance,BigDecimal reserve,boolean demo,BigDecimal currentBalance,LocalDate today,long revision) {}
    public record FlowInput(@NotNull FlowType type,@NotNull @DecimalMin("0.01") @Digits(integer=12,fraction=2) BigDecimal amount,
        @NotBlank @Size(max=200) String description,@NotBlank @Size(max=60) String category,
        @Size(max=120) String counterparty,@NotNull LocalDate expectedDate,@NotNull FlowStatus status,
        @NotNull Source source,UUID recurringRuleId,LocalDate occurrenceDate) {}
    public record FlowView(UUID id,FlowType type,BigDecimal amount,String description,String category,String counterparty,
        LocalDate expectedDate,FlowStatus status,Source source,UUID recurringRuleId,LocalDate occurrenceDate,Instant updatedAt) {
        public static FlowView of(Flow f) { return new FlowView(f.id,f.type,f.amount,f.description,f.category,f.counterparty,f.expectedDate,f.status,f.source,f.recurringRuleId,f.occurrenceDate,f.updatedAt); }
    }
    public record RuleInput(@NotNull FlowType type,@NotNull @DecimalMin("0.01") @Digits(integer=12,fraction=2) BigDecimal amount,
        @NotBlank @Size(max=200) String description,@NotBlank @Size(max=60) String category,
        @NotNull Frequency frequency,@NotNull LocalDate startDate,LocalDate endDate,boolean active) {}
    public record RuleView(UUID id,FlowType type,BigDecimal amount,String description,String category,Frequency frequency,LocalDate startDate,LocalDate endDate,boolean active) {
        public static RuleView of(Rule r) { return new RuleView(r.id,r.type,r.amount,r.description,r.category,r.frequency,r.startDate,r.endDate,r.active); }
    }
    public record PageView<T>(List<T> items,int page,int size,long totalElements,int totalPages) {
        public static <T> PageView<T> of(Page<T> p) { return new PageView<>(p.getContent(),p.getNumber(),p.getSize(),p.getTotalElements(),p.getTotalPages()); }
    }
}
