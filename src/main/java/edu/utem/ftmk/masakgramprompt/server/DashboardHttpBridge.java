package edu.utem.ftmk.masakgramprompt.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import edu.utem.ftmk.masakgramprompt.client.MasakGramPromptClient;
import edu.utem.ftmk.masakgramprompt.dao.ReportingDAO;
import edu.utem.ftmk.masakgramprompt.network.NetworkProtocol;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HTTP Bridge - converts browser requests into TCP/IP socket calls,
 * and serves reporting features (fact sheet, reel dashboard, CSV
 * export) directly via ReportingDAO.
 *
 * FIXED: now serves static files from the classpath (src/main/resources/static)
 * so that index.html is found regardless of the working directory.
 */
public class DashboardHttpBridge {
    private static final Logger logger = LoggerFactory.getLogger(DashboardHttpBridge.class);
    private static final int HTTP_PORT = 8081;
    private static final String SOCKET_HOST = "localhost";
    private static final int SOCKET_PORT = 5555;

    private final Gson gson = new Gson();
    private final ReportingDAO reportingDAO = new ReportingDAO();
    private final ExecutorService batchExecutor = Executors.newSingleThreadExecutor();
    private volatile BatchRunState currentBatch = new BatchRunState();

    private static class ExperimentStatus {
        int transcriptId;
        String technique;
        String status = "pending";
        String recipeName;
        int experimentId;
        String message;
    }

    private static class BatchRunState {
        volatile boolean running = false;
        String modelName;
        List<String> techniques = new ArrayList<>();
        long startTime;
        long endTime;
        AtomicInteger completed = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        int total;
        final Map<String, ExperimentStatus> statuses = new ConcurrentHashMap<>();
    }

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(HTTP_PORT), 0);

        MasakGramPromptClient testClient = new MasakGramPromptClient(SOCKET_HOST, SOCKET_PORT);
        if (testClient.connect()) {
            testClient.disconnect();
            logger.info("Verified socket server reachable at {}:{}", SOCKET_HOST, SOCKET_PORT);
        } else {
            logger.warn("Could not reach socket server at startup");
        }

        server.createContext("/api/models", this::handleGetModels);
        server.createContext("/api/techniques", this::handleGetTechniques);
        server.createContext("/api/transcripts", this::handleGetTranscripts);
        server.createContext("/api/dashboard", this::handleGetDashboard);
        server.createContext("/api/run-batch", this::handleRunBatch);
        server.createContext("/api/fact-sheet", this::handleFactSheet);
        server.createContext("/api/export-fact-sheet", this::handleExportFactSheet);
        server.createContext("/api/export", this::handleExport);
        server.createContext("/api/reels", this::handleReels);
        server.createContext("/api/reel-detail", this::handleReelDetail);
        server.createContext("/", this::handleStatic);

        server.setExecutor(null);
        server.start();

        logger.info("HTTP Bridge started on http://localhost:{}", HTTP_PORT);
    }

    // ----------------------------------------------------------------
    //  CORS and helper methods
    // ----------------------------------------------------------------

    private boolean handlePreflight(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return true;
        }
        return false;
    }

    private void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes("UTF-8");
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }

    private void sendCsv(HttpExchange exchange, String filename, String csv) throws IOException {
        byte[] bytes = csv.getBytes("UTF-8");
        exchange.getResponseHeaders().set("Content-Type", "text/csv; charset=UTF-8");
        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }

    private void sendError(HttpExchange exchange, int status, String message) throws IOException {
        JsonObject obj = new JsonObject();
        obj.addProperty("error", message);
        sendJson(exchange, status, gson.toJson(obj));
    }

    private MasakGramPromptClient newClient() {
        return new MasakGramPromptClient(SOCKET_HOST, SOCKET_PORT);
    }

    // ----------------------------------------------------------------
    //  API handlers (unchanged)
    // ----------------------------------------------------------------

    private void handleGetModels(HttpExchange exchange) throws IOException {
        if (handlePreflight(exchange)) return;
        MasakGramPromptClient client = newClient();
        try {
            if (!client.connect()) { sendError(exchange, 500, "Cannot connect to socket server"); return; }
            sendJson(exchange, 200, gson.toJson(client.getModels()));
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        } finally { client.disconnect(); }
    }

    private void handleGetTechniques(HttpExchange exchange) throws IOException {
        if (handlePreflight(exchange)) return;
        MasakGramPromptClient client = newClient();
        try {
            if (!client.connect()) { sendError(exchange, 500, "Cannot connect to socket server"); return; }
            sendJson(exchange, 200, gson.toJson(client.getTechniques()));
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        } finally { client.disconnect(); }
    }

    private void handleGetTranscripts(HttpExchange exchange) throws IOException {
        if (handlePreflight(exchange)) return;
        MasakGramPromptClient client = newClient();
        try {
            if (!client.connect()) { sendError(exchange, 500, "Cannot connect to socket server"); return; }
            sendJson(exchange, 200, gson.toJson(client.getTranscripts()));
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        } finally { client.disconnect(); }
    }

    private void handleGetDashboard(HttpExchange exchange) throws IOException {
        if (handlePreflight(exchange)) return;
        MasakGramPromptClient client = newClient();
        try {
            if (!client.connect()) { sendError(exchange, 500, "Cannot connect to socket server"); return; }
            List<Map<String, Object>> models = client.getModels();
            List<Map<String, Object>> techniques = client.getTechniques();
            List<Map<String, Object>> transcripts = client.getTranscripts();

            JsonObject dashboard = new JsonObject();
            dashboard.addProperty("totalModels", models.size());
            dashboard.addProperty("totalTechniques", techniques.size());
            dashboard.addProperty("totalTranscripts", transcripts.size());
            dashboard.addProperty("potentialExperiments", models.size() * techniques.size() * transcripts.size());
            sendJson(exchange, 200, gson.toJson(dashboard));
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        } finally { client.disconnect(); }
    }

    private void handleRunBatch(HttpExchange exchange) throws IOException {
        if (handlePreflight(exchange)) return;
        String method = exchange.getRequestMethod();

        if ("GET".equalsIgnoreCase(method)) {
            sendJson(exchange, 200, buildStatusJson());
            return;
        }

        if ("POST".equalsIgnoreCase(method)) {
            if (currentBatch.running) {
                sendError(exchange, 409, "A batch is already running. Wait for it to finish.");
                return;
            }
            String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
            JsonObject req = JsonParser.parseString(body).getAsJsonObject();
            String modelName = req.get("modelName").getAsString();

            List<String> techniques = new ArrayList<>();
            JsonArray techArray = req.getAsJsonArray("techniques");
            for (int i = 0; i < techArray.size(); i++) techniques.add(techArray.get(i).getAsString());

            if (modelName.isEmpty() || techniques.isEmpty()) {
                sendError(exchange, 400, "modelName and at least one technique are required");
                return;
            }

            startBatch(modelName, techniques);

            JsonObject resp = new JsonObject();
            resp.addProperty("status", "started");
            resp.addProperty("total", currentBatch.total);
            sendJson(exchange, 202, gson.toJson(resp));
            return;
        }

        sendError(exchange, 405, "Method not allowed");
    }

    private void handleFactSheet(HttpExchange exchange) throws IOException {
        if (handlePreflight(exchange)) return;
        Integer experimentId = parseIntParam(exchange.getRequestURI().getQuery(), "experimentId");
        if (experimentId == null) { sendError(exchange, 400, "experimentId query parameter required"); return; }
        JsonObject sheet = reportingDAO.getFactSheet(experimentId);
        if (sheet == null) { sendError(exchange, 404, "Fact sheet not found for experiment " + experimentId); return; }
        sendJson(exchange, 200, gson.toJson(sheet));
    }

    private void handleExportFactSheet(HttpExchange exchange) throws IOException {
        if (handlePreflight(exchange)) return;
        Integer experimentId = parseIntParam(exchange.getRequestURI().getQuery(), "experimentId");
        if (experimentId == null) { sendError(exchange, 400, "experimentId query parameter required"); return; }
        String csv = reportingDAO.exportFactSheetCsv(experimentId);
        if (csv == null) { sendError(exchange, 404, "No fact sheet data for experiment " + experimentId); return; }
        sendCsv(exchange, "fact_sheet_experiment_" + experimentId + ".csv", csv);
    }

    private void handleExport(HttpExchange exchange) throws IOException {
        if (handlePreflight(exchange)) return;
        String layer = parseStringParam(exchange.getRequestURI().getQuery(), "layer");
        if (layer == null || !reportingDAO.isValidLayer(layer)) {
            sendError(exchange, 400, "Unknown or missing layer parameter");
            return;
        }
        String csv = reportingDAO.exportLayerAsCsv(layer);
        if (csv == null) { sendError(exchange, 500, "Failed to generate export for " + layer); return; }
        sendCsv(exchange, layer + ".csv", csv);
    }

    private void handleReels(HttpExchange exchange) throws IOException {
        if (handlePreflight(exchange)) return;
        JsonArray reels = reportingDAO.getReelDashboard();
        sendJson(exchange, 200, gson.toJson(reels));
    }

    private void handleReelDetail(HttpExchange exchange) throws IOException {
        if (handlePreflight(exchange)) return;
        Integer transcriptId = parseIntParam(exchange.getRequestURI().getQuery(), "transcriptId");
        if (transcriptId == null) { sendError(exchange, 400, "transcriptId query parameter required"); return; }
        JsonObject detail = reportingDAO.getReelDetail(transcriptId);
        sendJson(exchange, 200, gson.toJson(detail));
    }

    // ----------------------------------------------------------------
    //  STATIC FILE SERVING – from classpath (src/main/resources/static)
    // ----------------------------------------------------------------

    private void handleStatic(HttpExchange exchange) throws IOException {
        if (handlePreflight(exchange)) return;

        String path = exchange.getRequestURI().getPath();
        if (path.equals("/") || path.isEmpty()) {
            path = "/index.html";
        }
        // basic traversal guard
        if (path.contains("..")) {
            exchange.sendResponseHeaders(403, -1);
            exchange.close();
            return;
        }

        // Build resource path (e.g., "static/index.html")
        String resourcePath = "static" + path;

        // Try to load from classpath
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                // Not found – send 404
                byte[] body = "404 Not Found".getBytes("UTF-8");
                exchange.sendResponseHeaders(404, body.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
                return;
            }

            // Read the file content
            byte[] bytes = is.readAllBytes();
            sendStaticResponse(exchange, bytes, path);
        } catch (IOException e) {
            logger.error("Error serving static file: {}", e.getMessage());
            exchange.sendResponseHeaders(500, -1);
        }
    }

    private void sendStaticResponse(HttpExchange exchange, byte[] bytes, String path) throws IOException {
        String contentType = "application/octet-stream";
        if (path.endsWith(".html")) contentType = "text/html; charset=UTF-8";
        else if (path.endsWith(".css")) contentType = "text/css; charset=UTF-8";
        else if (path.endsWith(".js")) contentType = "application/javascript; charset=UTF-8";
        else if (path.endsWith(".json")) contentType = "application/json; charset=UTF-8";
        else if (path.endsWith(".png")) contentType = "image/png";
        else if (path.endsWith(".svg")) contentType = "image/svg+xml";

        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }

    // ----------------------------------------------------------------
    //  Helper methods for batch and status
    // ----------------------------------------------------------------

    private Integer parseIntParam(String query, String name) {
        String v = parseStringParam(query, name);
        try { return v == null ? null : Integer.parseInt(v); } catch (NumberFormatException e) { return null; }
    }

    private String parseStringParam(String query, String name) {
        if (query == null) return null;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(name)) {
                return java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private List<Integer> fetchTranscriptIds() {
        List<Integer> ids = new ArrayList<>();
        MasakGramPromptClient client = newClient();
        try {
            if (client.connect()) {
                List<Map<String, Object>> transcripts = client.getTranscripts();
                for (Map<String, Object> t : transcripts) {
                    Object idObj = t.get("transcriptId");
                    if (idObj instanceof Number) {
                        ids.add(((Number) idObj).intValue());
                    }
                }
                client.disconnect();
            } else {
                logger.warn("Could not connect to socket server to fetch transcript IDs; using fallback.");
            }
        } catch (Exception e) {
            logger.error("Error fetching transcript IDs: {}", e.getMessage());
        }
        if (ids.isEmpty()) {
            logger.warn("No transcript IDs retrieved; using fallback IDs 1..50 (may fail).");
            for (int i = 1; i <= 50; i++) ids.add(i);
        }
        return ids;
    }

    private void startBatch(String modelName, List<String> techniques) {
        List<Integer> transcriptIds = fetchTranscriptIds();
        if (transcriptIds.isEmpty()) {
            logger.error("Cannot start batch: no transcript IDs available.");
            return;
        }

        BatchRunState state = new BatchRunState();
        state.modelName = modelName;
        state.techniques = techniques;
        state.running = true;
        state.startTime = System.currentTimeMillis();
        state.total = transcriptIds.size() * techniques.size();
        currentBatch = state;

        logger.info("Starting batch: model={}, techniques={}, total={}, transcripts={}",
                    modelName, techniques, state.total, transcriptIds);

        batchExecutor.submit(() -> {
            for (int transcriptId : transcriptIds) {
                for (String technique : techniques) {
                    String key = transcriptId + ":" + technique;
                    ExperimentStatus es = new ExperimentStatus();
                    es.transcriptId = transcriptId;
                    es.technique = technique;
                    es.status = "running";
                    state.statuses.put(key, es);

                    MasakGramPromptClient client = new MasakGramPromptClient(SOCKET_HOST, SOCKET_PORT);
                    try {
                        if (!client.connect()) {
                            es.status = "failed";
                            es.message = "could not connect";
                            state.failed.incrementAndGet();
                            continue;
                        }
                        NetworkProtocol.ExperimentResult result =
                                client.runExperiment(transcriptId, modelName, technique);
                        if (result != null && result.experimentId > 0) {
                            es.status = "completed";
                            es.recipeName = result.recipeName;
                            es.experimentId = result.experimentId;
                            state.completed.incrementAndGet();
                        } else {
                            es.status = "failed";
                            es.message = "server returned no result";
                            state.failed.incrementAndGet();
                        }
                    } catch (Exception e) {
                        es.status = "failed";
                        es.message = e.getMessage();
                        state.failed.incrementAndGet();
                        logger.error("Batch experiment error T={} tech={}: {}", transcriptId, technique, e.getMessage());
                    } finally {
                        client.disconnect();
                    }
                }
            }
            state.running = false;
            state.endTime = System.currentTimeMillis();
            logger.info("Batch complete: completed={}, failed={}", state.completed.get(), state.failed.get());
        });
    }

    private String buildStatusJson() {
        BatchRunState state = currentBatch;
        JsonObject obj = new JsonObject();
        obj.addProperty("running", state.running);
        obj.addProperty("modelName", state.modelName);
        obj.add("techniques", gson.toJsonTree(state.techniques));
        obj.addProperty("total", state.total);
        obj.addProperty("completed", state.completed.get());
        obj.addProperty("failed", state.failed.get());
        obj.addProperty("startTime", state.startTime);
        obj.addProperty("endTime", state.endTime);

        JsonArray statusArray = new JsonArray();
        for (ExperimentStatus es : state.statuses.values()) {
            JsonObject s = new JsonObject();
            s.addProperty("transcriptId", es.transcriptId);
            s.addProperty("technique", es.technique);
            s.addProperty("status", es.status);
            if (es.recipeName != null) s.addProperty("recipeName", es.recipeName);
            s.addProperty("experimentId", es.experimentId);
            if (es.message != null) s.addProperty("message", es.message);
            statusArray.add(s);
        }
        obj.add("statuses", statusArray);
        return gson.toJson(obj);
    }

    // ----------------------------------------------------------------
    //  Main entry point
    // ----------------------------------------------------------------

    public static void main(String[] args) throws IOException {
        System.out.println("==========================================");
        System.out.println("  MasakGramPrompt HTTP Bridge");
        System.out.println("==========================================");

        DashboardHttpBridge bridge = new DashboardHttpBridge();
        bridge.start();

        System.out.println("\nDashboard: http://localhost:8081/");
        System.out.println("HTTP Bridge API running on http://localhost:8081");
        System.out.println("Endpoints:");
        System.out.println("  GET  /api/models | /api/techniques | /api/transcripts | /api/dashboard");
        System.out.println("  GET/POST /api/run-batch");
        System.out.println("  GET  /api/fact-sheet?experimentId=N");
        System.out.println("  GET  /api/export-fact-sheet?experimentId=N");
        System.out.println("  GET  /api/export?layer=... (10 layers)");
        System.out.println("  GET  /api/reels");
        System.out.println("  GET  /api/reel-detail?transcriptId=N");
    }
}