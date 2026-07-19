package edu.utem.ftmk.masakgramprompt.client;

import edu.utem.ftmk.masakgramprompt.network.NetworkProtocol;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * MasakGramPromptClient - V2
 *
 * ROOT CAUSE FIXED: the server sends multiple StatusUpdate JSON lines
 * (progress callbacks) followed by ONE final ResponseMessage JSON line,
 * all over the same socket. The previous client read only the FIRST
 * line and misinterpreted a StatusUpdate as the ResponseMessage
 * (they share no common fields, so Gson silently fell back to the
 * ResponseMessage no-arg constructor defaults: success=true, data=null).
 *
 * This version loops reading lines and uses the "messageType" field
 * to tell StatusUpdate ("STATUS") apart from the real answer
 * ("RESPONSE"), printing progress along the way and only returning
 * once the real response arrives.
 */
public class MasakGramPromptClient {
    private static final Logger logger = LoggerFactory.getLogger(MasakGramPromptClient.class);

    private static final int SOCKET_TIMEOUT_MS = 5 * 60 * 1000; // 5 minutes

    private String serverHost;
    private int serverPort;
    private Socket socket;
    private BufferedReader input;
    private PrintWriter output;
    private volatile boolean connected;
    private final Gson gson = new Gson();

    public MasakGramPromptClient(String host, int port) {
        this.serverHost = host;
        this.serverPort = port;
        this.connected = false;
    }

    public boolean connect() {
        try {
            socket = new Socket(serverHost, serverPort);
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);

            input = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), "UTF-8"));
            output = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

            connected = true;
            logger.info("Connected to MasakGramPrompt Server at {}:{} (timeout={}ms)",
                       serverHost, serverPort, SOCKET_TIMEOUT_MS);
            return true;
        } catch (IOException e) {
            logger.error("Failed to connect to server: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Send a request and read lines until the real ResponseMessage arrives.
     * Any StatusUpdate lines received along the way are logged/printed
     * as progress (and passed to the optional callback) but do NOT
     * end the wait.
     */
    public NetworkProtocol.ResponseMessage sendRequest(NetworkProtocol.RequestMessage request) {
        return sendRequest(request, null);
    }

    public NetworkProtocol.ResponseMessage sendRequest(NetworkProtocol.RequestMessage request,
                                                        StatusListener statusListener) {
        if (!connected) {
            logger.error("Not connected to server");
            return null;
        }

        try {
            String requestJson = request.toJson();
            output.println(requestJson);
            logger.debug("Sent request: {}", request.command);

            // Loop: keep reading lines until we get the actual RESPONSE,
            // skipping any STATUS progress lines along the way.
            while (true) {
                String line = input.readLine();
                if (line == null) {
                    logger.error("Connection closed by server");
                    connected = false;
                    return null;
                }

                String messageType = peekMessageType(line);

                if ("STATUS".equals(messageType)) {
                    NetworkProtocol.StatusUpdate update = NetworkProtocol.StatusUpdate.fromJson(line);
                    if (statusListener != null) {
                        statusListener.onStatus(update);
                    } else {
                        logger.debug("[{}%] {} - {}", update.progress, update.status, update.message);
                    }
                    continue; // keep waiting for the real response
                }

                // Anything else (including messageType == "RESPONSE" or
                // missing/unknown for backward compatibility) is treated
                // as the final response.
                NetworkProtocol.ResponseMessage response =
                        NetworkProtocol.ResponseMessage.fromJson(line);
                logger.debug("Received response with status: {}", response.statusCode);
                return response;
            }

        } catch (SocketTimeoutException e) {
            logger.error("TIMEOUT waiting for response to {} (waited {}ms).",
                       request.command, SOCKET_TIMEOUT_MS);
            return null;
        } catch (IOException e) {
            logger.error("Error sending request: {}", e.getMessage());
            connected = false;
            return null;
        }
    }

    /**
     * Cheaply peek at the "messageType" field of a JSON line without
     * fully deserializing it into a specific class.
     */
    private String peekMessageType(String jsonLine) {
        try {
            JsonObject obj = JsonParser.parseString(jsonLine).getAsJsonObject();
            if (obj.has("messageType")) {
                return obj.get("messageType").getAsString();
            }
        } catch (JsonSyntaxException | IllegalStateException e) {
            logger.warn("Could not peek messageType from line: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Optional callback for live status updates (pending/running/completed).
     */
    @FunctionalInterface
    public interface StatusListener {
        void onStatus(NetworkProtocol.StatusUpdate update);
    }

    public boolean ping() {
        NetworkProtocol.RequestMessage request = new NetworkProtocol.RequestMessage();
        request.requestId = "ping_" + System.currentTimeMillis();
        request.command = NetworkProtocol.Commands.PING;

        NetworkProtocol.ResponseMessage response = sendRequest(request);
        return response != null && response.success;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getModels() {
        NetworkProtocol.RequestMessage request = new NetworkProtocol.RequestMessage();
        request.requestId = "models_" + System.currentTimeMillis();
        request.command = NetworkProtocol.Commands.GET_MODELS;

        NetworkProtocol.ResponseMessage response = sendRequest(request);
        if (response != null && response.success) {
            return (List<Map<String, Object>>) response.data;
        }
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getTechniques() {
        NetworkProtocol.RequestMessage request = new NetworkProtocol.RequestMessage();
        request.requestId = "techniques_" + System.currentTimeMillis();
        request.command = NetworkProtocol.Commands.GET_TECHNIQUES;

        NetworkProtocol.ResponseMessage response = sendRequest(request);
        if (response != null && response.success) {
            return (List<Map<String, Object>>) response.data;
        }
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getTranscripts() {
        NetworkProtocol.RequestMessage request = new NetworkProtocol.RequestMessage();
        request.requestId = "transcripts_" + System.currentTimeMillis();
        request.command = NetworkProtocol.Commands.GET_TRANSCRIPTS;

        NetworkProtocol.ResponseMessage response = sendRequest(request);
        if (response != null && response.success) {
            return (List<Map<String, Object>>) response.data;
        }
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getDashboard() {
        NetworkProtocol.RequestMessage request = new NetworkProtocol.RequestMessage();
        request.requestId = "dashboard_" + System.currentTimeMillis();
        request.command = NetworkProtocol.Commands.GET_DASHBOARD;

        NetworkProtocol.ResponseMessage response = sendRequest(request);
        if (response != null && response.success) {
            return (Map<String, Object>) response.data;
        }
        return new HashMap<>();
    }

    /**
     * Run an experiment. Blocks until the real ExperimentResult is
     * returned (status updates along the way are logged, not returned).
     */
    public NetworkProtocol.ExperimentResult runExperiment(
            int transcriptId, String modelName, String technique) {
        return runExperiment(transcriptId, modelName, technique, null);
    }

    public NetworkProtocol.ExperimentResult runExperiment(
            int transcriptId, String modelName, String technique, StatusListener statusListener) {

        NetworkProtocol.RequestMessage request = new NetworkProtocol.RequestMessage();
        request.requestId = "exp_" + System.currentTimeMillis();
        request.command = NetworkProtocol.Commands.RUN_EXPERIMENT;
        request.transcriptId = transcriptId;
        request.modelName = modelName;
        request.technique = technique;

        NetworkProtocol.ResponseMessage response = sendRequest(request, statusListener);

        if (response == null) {
            logger.warn("No response received (timeout or connection error)");
            return null;
        }

        if (!response.success) {
            logger.warn("Experiment failed on server (statusCode={}, error={})",
                       response.statusCode, response.error);
            return null;
        }

        if (response.data == null) {
            logger.warn("Experiment succeeded but data is null");
            return null;
        }

        try {
            String dataJson = gson.toJson(response.data);
            return gson.fromJson(dataJson, NetworkProtocol.ExperimentResult.class);
        } catch (Exception e) {
            logger.error("Failed to convert response data to ExperimentResult: {}", e.getMessage());
            return null;
        }
    }

    public void disconnect() {
        try {
            if (input != null) input.close();
            if (output != null) output.close();
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            connected = false;
            logger.info("Disconnected from server");
        } catch (IOException e) {
            logger.error("Error disconnecting: {}", e.getMessage());
        }
    }

    public boolean isConnected() {
        return connected;
    }

    public static void main(String[] args) {
        String serverHost = "localhost";
        int serverPort = 5555;

        if (args.length >= 2) {
            serverHost = args[0];
            serverPort = Integer.parseInt(args[1]);
        }

        MasakGramPromptClient client = new MasakGramPromptClient(serverHost, serverPort);

        if (!client.connect()) {
            System.err.println("Failed to connect to server");
            System.exit(1);
        }

        try {
            System.out.println("\n=== Testing Ping ===");
            System.out.println(client.ping() ? "Server is responsive" : "Server ping failed");

            System.out.println("\n=== Available LLM Models ===");
            List<Map<String, Object>> models = client.getModels();
            for (Map<String, Object> model : models) {
                System.out.println("  - " + model.get("modelName"));
            }

            System.out.println("\n=== Available Prompt Techniques ===");
            List<Map<String, Object>> techniques = client.getTechniques();
            for (Map<String, Object> tech : techniques) {
                System.out.println("  - " + tech.get("techniqueName"));
            }

            System.out.println("\n=== Available Transcripts ===");
            List<Map<String, Object>> transcripts = client.getTranscripts();
            System.out.println("Total transcripts: " + transcripts.size());

            System.out.println("\n=== Dashboard Summary ===");
            Map<String, Object> dashboard = client.getDashboard();
            for (Map.Entry<String, Object> entry : dashboard.entrySet()) {
                System.out.println("  " + entry.getKey() + ": " + entry.getValue());
            }

            if (!transcripts.isEmpty()) {
                System.out.println("\n=== Running Experiment (with live status) ===");
                int transcriptId = ((Number) transcripts.get(0).get("transcriptId")).intValue();
                String modelName = "Llama 3.2 3B Instruct";
                String technique = "zero-shot";

                NetworkProtocol.ExperimentResult result = client.runExperiment(
                        transcriptId, modelName, technique,
                        update -> System.out.println("  [" + update.progress + "%] "
                                + update.status + " - " + update.message));

                if (result != null) {
                    System.out.println("Experiment completed");
                    System.out.println("  Recipe: " + result.recipeName);
                    System.out.println("  Servings: " + result.servingsEstimated);
                } else {
                    System.out.println("Experiment failed");
                }
            }

        } finally {
            client.disconnect();
        }
    }
}