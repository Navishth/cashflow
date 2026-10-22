package com.cashflow.data;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

/** Persistence only. Controllers return explicit DTOs, never these entities. */
public final class Models {
    private Models() {}
    public enum FlowType { INFLOW, OUTFLOW }
    public enum FlowStatus { PLANNED, RECEIVED, PAID, CANCELLED }
    public enum Source { SCHEDULED, UNPLANNED }
    public enum Frequency { WEEKLY, MONTHLY }
    public enum ChangeKind { RESCHEDULE, AMOUNT_CHANGE, ADD_CASH_FLOW, REMOVE_CASH_FLOW }
    @MappedSuperclass public static abstract class Record {
        @Id public UUID id = UUID.randomUUID();
        public Instant createdAt = Instant.now();
        public Instant updatedAt = Instant.now();
        @PreUpdate public void updated() { updatedAt = Instant.now(); }
    }
    @Entity(name="AppUser") @Table(name="users") public static class User extends Record {
        public String email, displayName, passwordHash;
        public boolean verified;
        public int authVersion;
    }
    @Entity @Table(name="user_identities") public static class Identity extends Record {
        public UUID userId;
        public String provider, subject;
    }
    @Entity @Table(name="auth_tokens") public static class Token extends Record {
        public UUID userId;
        public String kind, tokenHash;
        public Instant expiresAt, usedAt;
    }
    @Entity @Table(name="businesses") public static class Business extends Record {
        public UUID ownerId;
        public String name, currency, timezone;
        public LocalDate openingDate;
        @Column(precision=16,scale=2) public BigDecimal openingBalance, reserve;
        public boolean demo;
        @Version public long revision;
    }
    @Entity @Table(name="cash_flows") public static class Flow extends Record {
        public UUID businessId;
        @Enumerated(EnumType.STRING) public FlowType type;
        @Column(precision=16,scale=2) public BigDecimal amount;
        public String description, category, counterparty;
        public LocalDate expectedDate;
        @Enumerated(EnumType.STRING) public FlowStatus status;
        @Enumerated(EnumType.STRING) public Source source;
        public UUID recurringRuleId;
        public LocalDate occurrenceDate;
    }
    @Entity @Table(name="recurring_rules") public static class Rule extends Record {
        public UUID businessId;
        @Enumerated(EnumType.STRING) public FlowType type;
        @Column(precision=16,scale=2) public BigDecimal amount;
        public String description, category;
        @Enumerated(EnumType.STRING) public Frequency frequency;
        public LocalDate startDate, endDate;
        public boolean active;
    }
    @Entity @Table(name="forecasts") public static class Forecast extends Record {
        public UUID businessId;
        public LocalDate startDate;
        @Column(precision=16,scale=2) public BigDecimal openingBalance, reserve;
        public long inputRevision;
        public String modelName, modelVersion;
        @Column(columnDefinition="text") public String limitations, events;
    }
    @MappedSuperclass public static abstract class Point {
        @Id public UUID id = UUID.randomUUID();
        public LocalDate date;
        @Column(precision=16,scale=2) public BigDecimal predictedUnplannedNet, scheduledInflows, scheduledOutflows, closingBalance;
    }
    @Entity @Table(name="forecast_points") public static class ForecastPoint extends Point { public UUID forecastId; }
    @Entity @Table(name="scenarios") public static class Scenario extends Record {
        public UUID businessId, baselineId;
        public String name, description;
        public Instant simulatedAt;
    }
    @Entity @Table(name="scenario_changes") public static class Change {
        @Id public UUID id = UUID.randomUUID();
        public UUID scenarioId;
        @Enumerated(EnumType.STRING) public ChangeKind kind;
        public String targetEventId;
        public LocalDate newDate;
        @Column(precision=16,scale=2) public BigDecimal amount;
        @Enumerated(EnumType.STRING) public FlowType type;
        public String description;
        public int position;
    }
    @Entity @Table(name="scenario_points") public static class ScenarioPoint extends Point { public UUID scenarioId; }
}
