package com.benzaazaa.parcel.notification;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Tiny HTTP stub of the external SMS API (JDK HttpServer, no extra dependency). */
public class StubSmsProvider implements AutoCloseable {

    public enum Mode { OK, FAIL_503, BAD_REQUEST, SLOW, FAIL_ONCE_THEN_OK }

    private final HttpServer server;
    private final AtomicReference<Mode> mode = new AtomicReference<>(Mode.OK);
    private final AtomicInteger hits = new AtomicInteger();

    public StubSmsProvider() {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/v1/messages", exchange -> {
            int n = hits.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            Mode m = mode.get();
            int status;
            String body;
            if (m == Mode.FAIL_503 || (m == Mode.FAIL_ONCE_THEN_OK && n == 1)) {
                status = 503;
                body = "{\"error\":\"down\"}";
            } else if (m == Mode.BAD_REQUEST) {
                status = 400;
                body = "{\"error\":\"invalid number\"}";
            } else {
                if (m == Mode.SLOW) {
                    try {
                        Thread.sleep(1_500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                status = 202;
                body = "{\"id\":\"sms_" + n + "\",\"status\":\"QUEUED\"}";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    public String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    public void mode(Mode m) {
        mode.set(m);
        hits.set(0);
    }

    public int hits() {
        return hits.get();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
