package com.cashflow.forecast;
import com.cashflow.forecast.ForecastDtos.ForecastView;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/forecasts")
public class ForecastController {
    private final ForecastService service;
    public ForecastController(ForecastService service) { this.service=service; }
    @PostMapping("/generate") ForecastView generate() { return service.generate(); }
    @GetMapping("/latest") ForecastView latest() { return service.latest(); }
    @GetMapping("/{id}") ForecastView get(@PathVariable UUID id) { return service.view(service.owned(id)); }
}
