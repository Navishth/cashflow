package com.cashflow.data;

import com.cashflow.data.Models.*;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public final class Repositories {
    private Repositories() {}
    public interface Users extends JpaRepository<User,UUID> { Optional<User> findByEmail(String email); }
    public interface Identities extends JpaRepository<Identity,UUID> { Optional<Identity> findByProviderAndSubject(String provider,String subject); }
    public interface Tokens extends JpaRepository<Token,UUID> {
        @Lock(LockModeType.PESSIMISTIC_WRITE) Optional<Token> findByTokenHashAndKind(String hash,String kind);
        void deleteByUserIdAndKind(UUID userId,String kind);
    }
    public interface Businesses extends JpaRepository<Business,UUID> { Optional<Business> findByOwnerId(UUID ownerId); }
    public interface Flows extends JpaRepository<Flow,UUID>, JpaSpecificationExecutor<Flow> {
        List<Flow> findByBusinessId(UUID businessId);
        Optional<Flow> findByIdAndBusinessId(UUID id,UUID businessId);
        boolean existsByRecurringRuleId(UUID id);
    }
    public interface Rules extends JpaRepository<Rule,UUID> {
        List<Rule> findByBusinessIdOrderByStartDate(UUID businessId);
        Optional<Rule> findByIdAndBusinessId(UUID id,UUID businessId);
    }
    public interface Forecasts extends JpaRepository<Forecast,UUID> {
        Optional<Forecast> findByIdAndBusinessId(UUID id,UUID businessId);
        Optional<Forecast> findFirstByBusinessIdOrderByCreatedAtDesc(UUID businessId);
    }
    public interface ForecastPoints extends JpaRepository<ForecastPoint,UUID> { List<ForecastPoint> findByForecastIdOrderByDate(UUID id); }
    public interface Scenarios extends JpaRepository<Scenario,UUID> {
        Optional<Scenario> findByIdAndBusinessId(UUID id,UUID businessId);
        Page<Scenario> findByBusinessId(UUID id,Pageable pageable);
    }
    public interface Changes extends JpaRepository<Change,UUID> {
        List<Change> findByScenarioIdOrderByPosition(UUID id);
        void deleteByScenarioId(UUID id);
    }
    public interface ScenarioPoints extends JpaRepository<ScenarioPoint,UUID> {
        List<ScenarioPoint> findByScenarioIdOrderByDate(UUID id);
        void deleteByScenarioId(UUID id);
    }
}
