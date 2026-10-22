package com.cashflow;

import com.cashflow.forecast.MlClient;
import com.cashflow.forecast.ForecastDtos.History;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MlContractTest {
    @Test void slowInferenceFallsBackWithinTheReadTimeout() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var executor=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/predict",exchange->{
            try { Thread.sleep(10_000); } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        try {
            var start=LocalDate.of(2026,10,20);
            var history=java.util.stream.IntStream.range(0,28).mapToObj(i->new History(start.minusDays(28-i),BigDecimal.ONE)).toList();
            var client=new MlClient("http://127.0.0.1:"+server.getAddress().getPort(),"test-key-at-least-32-characters");
            long began=System.nanoTime();
            var prediction=client.predict(history,start,true);
            assertThat(java.time.Duration.ofNanos(System.nanoTime()-began)).isLessThan(java.time.Duration.ofSeconds(6));
            assertThat(prediction.modelVersion()).isEqualTo("java-baseline-v1");
            assertThat(prediction.predictions()).hasSize(30);
        } finally { server.stop(0); executor.shutdownNow(); executor.close(); }
    }
    @Test void wireDatesAreIsoStringsAndInvalidResponsesFallBack() throws Exception {
        var received=new AtomicReference<String>();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/predict",exchange->{
            received.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            byte[] body="{\"predictions\":[],\"modelName\":\"invalid\",\"modelVersion\":\"bad\",\"limitations\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(200,body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            var start=LocalDate.of(2026,10,20);
            var history=java.util.stream.IntStream.range(0,28).mapToObj(i->new History(start.minusDays(28-i),BigDecimal.ONE)).toList();
            var client=new MlClient("http://127.0.0.1:"+server.getAddress().getPort(),"test-key-at-least-32-characters");
            var prediction=client.predict(history,start,true);
            var json=new ObjectMapper().readTree(received.get());
            assertThat(json.get("startDate").isTextual()).isTrue();
            assertThat(json.get("startDate").asText()).isEqualTo("2026-10-20");
            assertThat(json.at("/history/0/date").isTextual()).isTrue();
            assertThat(prediction.modelVersion()).isEqualTo("java-baseline-v1");
        } finally { server.stop(0); }
    }
}
