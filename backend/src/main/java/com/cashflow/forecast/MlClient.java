package com.cashflow.forecast;

import com.cashflow.forecast.ForecastDtos.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class MlClient {
    private final RestClient client;
    public MlClient(@Value("${app.ml-url}") String url,@Value("${app.ml-key}") String key) {
        var factory=new SimpleClientHttpRequestFactory(); factory.setConnectTimeout(Duration.ofSeconds(2)); factory.setReadTimeout(Duration.ofSeconds(3));
        var mapper=Jackson2ObjectMapperBuilder.json().featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
        client=RestClient.builder().baseUrl(url).requestFactory(factory).defaultHeader("X-Internal-Key",key)
            .messageConverters(converters->{
                converters.removeIf(c->c instanceof MappingJackson2HttpMessageConverter);
                converters.add(new MappingJackson2HttpMessageConverter(mapper));
            }).build();
    }
    public Prediction predict(List<History> history,LocalDate start,boolean demo) {
        if(history.size()<28) return FinancialEngine.fallback(history,start,"Not enough history for model inference.");
        try {
            Prediction result=client.post().uri("/predict").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .accept(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(Map.of("schemaVersion","1","history",history,"startDate",start,"horizonDays",30,"allowSyntheticModel",demo)).retrieve().body(Prediction.class);
            if(result==null || result.predictions()==null || result.predictions().size()!=30 || result.modelName()==null || result.modelVersion()==null || result.limitations()==null) throw new IllegalArgumentException();
            for(int i=0;i<30;i++) {
                var p=result.predictions().get(i);
                if(!start.plusDays(i).equals(p.date()) || p.netFlow()==null || p.netFlow().abs().compareTo(new BigDecimal("1000000000000"))>0) throw new IllegalArgumentException();
            }
            return result;
        } catch(Exception e) { return FinancialEngine.fallback(history,start,"The inference service was unavailable or returned invalid output. A local baseline was used."); }
    }
}
