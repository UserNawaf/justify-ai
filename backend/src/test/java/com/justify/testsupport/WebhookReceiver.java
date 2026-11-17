package com.justify.testsupport;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** Tiny local HTTP receiver. Answers {@code failFirst} requests with 500, then 204. */
public class WebhookReceiver implements AutoCloseable {

    public record Received(String body, Map<String, List<String>> headers) {
        public String header(String name) {
            return headers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name))
                    .map(e -> e.getValue().get(0)).findFirst().orElse(null);
        }
    }

    private final HttpServer server;
    private final List<Received> received = new CopyOnWriteArrayList<>();
    private final AtomicInteger hits = new AtomicInteger();

    public WebhookReceiver(int failFirst) {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/hook", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            int n = hits.incrementAndGet();
            if (n <= failFirst) {
                exchange.sendResponseHeaders(500, -1);
            } else {
                received.add(new Received(body, Map.copyOf(exchange.getRequestHeaders())));
                exchange.sendResponseHeaders(204, -1);
            }
            exchange.close();
        });
        server.start();
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/hook";
    }

    public List<Received> received() {
        return received;
    }

    public int hits() {
        return hits.get();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
