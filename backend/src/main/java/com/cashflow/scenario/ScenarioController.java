package com.cashflow.scenario;
import com.cashflow.finance.FinanceDtos.PageView;
import com.cashflow.scenario.ScenarioDtos.*;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/scenarios")
public class ScenarioController {
    private final ScenarioService service;
    public ScenarioController(ScenarioService service) { this.service=service; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) ScenarioView create(@Valid @RequestBody ScenarioInput d) { return service.save(null,d); }
    @PatchMapping("/{id}") ScenarioView edit(@PathVariable UUID id,@Valid @RequestBody ScenarioInput d) { return service.save(id,d); }
    @GetMapping PageView<ScenarioView> list(@RequestParam(defaultValue="0") int page) { return service.list(page); }
    @GetMapping("/{id}") ScenarioView get(@PathVariable UUID id) { return service.view(service.owned(id)); }
    @PostMapping("/{id}/simulate") Comparison simulate(@PathVariable UUID id) { return service.simulate(id); }
    @GetMapping("/{id}/compare") Comparison compare(@PathVariable UUID id) { return service.compare(id); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void delete(@PathVariable UUID id) { service.delete(id); }
}
