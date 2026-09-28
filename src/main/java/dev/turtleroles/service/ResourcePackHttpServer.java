package dev.turtleroles.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/** Serves only the embedded public pack on an allocated hosting port. */
public final class ResourcePackHttpServer {
    private static final String RESOURCE = "generated-resource-pack/ConquestSMP-resource-pack.zip";
    private final Plugin plugin;
    private final int port;
    private HttpServer server;
    private java.util.concurrent.ExecutorService executor;

    public ResourcePackHttpServer(Plugin plugin, int port) {
        this.plugin = plugin;
        this.port = port;
    }

    public void start() throws IOException {
        byte[] pack;
        try (InputStream input = plugin.getResource(RESOURCE)) {
            if (input == null) {
                throw new IOException("Embedded resource pack is missing: " + RESOURCE);
            }
            pack = input.readAllBytes();
        }

        server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/turtleroles.zip", exchange -> serve(exchange, pack));
        server.createContext("/conquestsmp.zip", exchange -> serve(exchange, pack));
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.start();
        plugin.getLogger().info("Serving the Conquest SMP resource pack on HTTP port " + port + ".");
    }

    private void serve(HttpExchange exchange, byte[] pack) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            if (!path.equals("/turtleroles.zip") && !path.equals("/conquestsmp.zip")) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            String method = exchange.getRequestMethod();
            if (!method.equals("GET") && !method.equals("HEAD")) {
                exchange.getResponseHeaders().set("Allow", "GET, HEAD");
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.getResponseHeaders().set("Content-Length", Integer.toString(pack.length));
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"ConquestSMP-resource-pack.zip\"");
            exchange.sendResponseHeaders(200, method.equals("HEAD") ? -1 : pack.length);
            if (method.equals("GET")) {
                exchange.getResponseBody().write(pack);
            }
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }
}
