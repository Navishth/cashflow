package com.cashflow.finance;

import com.cashflow.finance.FinanceDtos.*;
import com.cashflow.data.Models.*;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1")
public class FinanceController {
    private final FinanceService service;
    public FinanceController(FinanceService service) { this.service=service; }
    @PostMapping("/businesses") @ResponseStatus(HttpStatus.CREATED) BusinessView create(@Valid @RequestBody BusinessInput d) { return service.createBusiness(d); }
    @GetMapping("/businesses/current") BusinessView current() { return service.businessView(service.current()); }
    @PatchMapping("/businesses/current") BusinessView update(@Valid @RequestBody BusinessInput d) { return service.updateBusiness(d); }
    @GetMapping("/cash-flows") PageView<FlowView> flows(@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,
        @RequestParam(required=false) FlowType type,@RequestParam(required=false) String category,@RequestParam(required=false) FlowStatus status,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return service.listFlows(from,to,type,category,status,page,size); }
    @GetMapping("/cash-flows/{id}") FlowView get(@PathVariable UUID id) { return service.getFlow(id); }
    @PostMapping("/cash-flows") @ResponseStatus(HttpStatus.CREATED) FlowView createFlow(@Valid @RequestBody FlowInput d) { return service.saveFlow(null,d); }
    @PatchMapping("/cash-flows/{id}") FlowView updateFlow(@PathVariable UUID id,@Valid @RequestBody FlowInput d) { return service.saveFlow(id,d); }
    @DeleteMapping("/cash-flows/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void deleteFlow(@PathVariable UUID id) { service.deleteFlow(id); }
    @GetMapping("/recurring-rules") List<RuleView> rules() { return service.listRules(); }
    @PostMapping("/recurring-rules") @ResponseStatus(HttpStatus.CREATED) RuleView createRule(@Valid @RequestBody RuleInput d) { return service.saveRule(null,d); }
    @PatchMapping("/recurring-rules/{id}") RuleView updateRule(@PathVariable UUID id,@Valid @RequestBody RuleInput d) { return service.saveRule(id,d); }
    @DeleteMapping("/recurring-rules/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void deleteRule(@PathVariable UUID id) { service.deleteRule(id); }
}
