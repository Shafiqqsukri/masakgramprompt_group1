package edu.utem.ftmk.masakgramprompt.server;

import edu.utem.ftmk.masakgramprompt.db.DatabaseConnection;
import edu.utem.ftmk.masakgramprompt.network.NetworkProtocol;
import edu.utem.ftmk.masakgramprompt.service.ExperimentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * MasakGramPromptServer is the main TCP/IP server for the project.
 * It listens for incoming client connections and processes requests.
 * 
 * Server runs on localhost:5555 by default.
 * Change PORT constant to use a different port.
 */
public class MasakGramPromptServer {
    private static final Logger logger = LoggerFactory.getLogger(MasakGramPromptServer.class);
    
    private static final int PORT = 5555;
    private static final int THREAD_POOL_SIZE = 10;
    private static final int BACKLOG = 50;
    
    private ServerSocket serverSocket;
    private ExecutorService clientThreadPool;
    private ExperimentService experimentService;
    private DatabaseConnection dbConnection;
    private volatile boolean running;
    
    public MasakGramPromptServer() {
        this.clientThreadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        this.experimentService = new ExperimentService();
        this.dbConnection = DatabaseConnection.getInstance();
        this.running = false;
    }
    
    /**
     * Start the server
     * @return true if server started successfully
     */
    public boolean start() {
        try {
            // Initialize database connection
            if (!dbConnection.connect()) {
                logger.error("Failed to connect to database");
                return false;
            }
            
            // Create server socket
            serverSocket = new ServerSocket(PORT, BACKLOG, InetAddress.getByName("0.0.0.0"));
            running = true;
            
            logger.info("MasakGramPrompt Server started on port {}", PORT);
            logger.info("Waiting for client connections...");
            
            return true;
        } catch (IOException e) {
            logger.error("Failed to start server: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Accept incoming client connections
     */
    public void acceptConnections() {
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                logger.info("New client connection from: {}", clientSocket.getInetAddress());
                
                // Handle client in separate thread
                ClientHandler handler = new ClientHandler(clientSocket, experimentService);
                clientThreadPool.execute(handler);
                
            } catch (SocketException e) {
                if (running) {
                    logger.error("Socket error: {}", e.getMessage());
                }
            } catch (IOException e) {
                if (running) {
                    logger.error("Error accepting client connection: {}", e.getMessage());
                }
            }
        }
    }
    
    /**
     * Stop the server
     */
    public void stop() {
        running = false;
        
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            logger.error("Error closing server socket: {}", e.getMessage());
        }
        
        clientThreadPool.shutdown();
        try {
            if (!clientThreadPool.awaitTermination(10, TimeUnit.SECONDS)) {
                clientThreadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            clientThreadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        experimentService.shutdown();
        dbConnection.disconnect();
        
        logger.info("MasakGramPrompt Server stopped");
    }
    
    /**
     * Main entry point
     */
    public static void main(String[] args) {
        MasakGramPromptServer server = new MasakGramPromptServer();
        
        if (!server.start()) {
            System.exit(1);
        }
        
        // Add shutdown hook for graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown signal received");
            server.stop();
        }));
        
        // Accept connections (blocking)
        server.acceptConnections();
    }
    
    /**
     * Inner class to handle individual client connections
     */
    private static class ClientHandler implements Runnable {
        private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);
        
        private Socket clientSocket;
        private ExperimentService experimentService;
        private BufferedReader input;
        private PrintWriter output;
        
        public ClientHandler(Socket socket, ExperimentService service) {
            this.clientSocket = socket;
            this.experimentService = service;
        }
        
        @Override
        public void run() {
            try {
                // Setup streams
                input = new BufferedReader(
                        new InputStreamReader(clientSocket.getInputStream(), "UTF-8"));
                output = new PrintWriter(
                        new OutputStreamWriter(clientSocket.getOutputStream(), "UTF-8"), true);
                
                String line;
                while ((line = input.readLine()) != null) {
                    processRequest(line);
                }
                
            } catch (IOException e) {
                logger.error("Error handling client: {}", e.getMessage());
            } finally {
                closeConnection();
            }
        }
        
        /**
         * Process incoming request from client
         */
        private void processRequest(String requestJson) {
            try {
                NetworkProtocol.RequestMessage request = 
                        NetworkProtocol.RequestMessage.fromJson(requestJson);
                
                logger.info("Processing request: {} from client", request.command);
                
                NetworkProtocol.ResponseMessage response = new NetworkProtocol.ResponseMessage();
                response.requestId = request.requestId;
                
                switch (request.command) {
                    case NetworkProtocol.Commands.RUN_EXPERIMENT:
                        handleRunExperiment(request, response);
                        break;
                    
                    case NetworkProtocol.Commands.GET_STATUS:
                        handleGetStatus(request, response);
                        break;
                    
                    case NetworkProtocol.Commands.GET_MODELS:
                        handleGetModels(request, response);
                        break;
                    
                    case NetworkProtocol.Commands.GET_TECHNIQUES:
                        handleGetTechniques(request, response);
                        break;
                    
                    case NetworkProtocol.Commands.GET_TRANSCRIPTS:
                        handleGetTranscripts(request, response);
                        break;
                    
                    case NetworkProtocol.Commands.GET_DASHBOARD:
                        handleGetDashboard(request, response);
                        break;
                    
                    case NetworkProtocol.Commands.PING:
                        handlePing(request, response);
                        break;
                    
                    default:
                        response.statusCode = NetworkProtocol.StatusCodes.BAD_REQUEST;
                        response.statusMessage = "Unknown command";
                        response.success = false;
                        response.error = "Command not recognized: " + request.command;
                }
                
                // Send response
                output.println(response.toJson());
                
            } catch (Exception e) {
                logger.error("Error processing request: {}", e.getMessage(), e);
                NetworkProtocol.ResponseMessage errorResponse = new NetworkProtocol.ResponseMessage();
                errorResponse.statusCode = NetworkProtocol.StatusCodes.INTERNAL_ERROR;
                errorResponse.statusMessage = "Internal Server Error";
                errorResponse.success = false;
                errorResponse.error = e.getMessage();
                output.println(errorResponse.toJson());
            }
        }
        
        /**
         * Handle RUN_EXPERIMENT request
         */
        private void handleRunExperiment(NetworkProtocol.RequestMessage request,
                                        NetworkProtocol.ResponseMessage response) {
            try {
                int transcriptId = request.transcriptId;
                String modelName = request.modelName;
                String technique = request.technique;
                
                if (transcriptId <= 0 || modelName == null || technique == null) {
                    response.statusCode = NetworkProtocol.StatusCodes.BAD_REQUEST;
                    response.statusMessage = "Invalid parameters";
                    response.success = false;
                    response.error = "transcriptId, modelName, and technique are required";
                    return;
                }
                
                // Run experiment with status callback
                NetworkProtocol.ExperimentResult result = experimentService.runExperiment(
                        transcriptId, modelName, technique,
                        (expId, model, tech, status, progress, msg) -> {
                            NetworkProtocol.StatusUpdate update = new NetworkProtocol.StatusUpdate();
                            update.experimentId = String.valueOf(expId);
                            update.modelName = model;
                            update.technique = tech;
                            update.status = status;
                            update.progress = progress;
                            update.message = msg;
                            
                            // Send status update to client
                            output.println(update.toJson());
                        });
                
                if (result == null) {
                    response.statusCode = NetworkProtocol.StatusCodes.INTERNAL_ERROR;
                    response.statusMessage = "Experiment execution failed";
                    response.success = false;
                    response.error = "Failed to execute experiment";
                } else {
                    response.statusCode = NetworkProtocol.StatusCodes.OK;
                    response.statusMessage = "Experiment completed";
                    response.success = true;
                    response.data = result;
                }
                
            } catch (Exception e) {
                response.statusCode = NetworkProtocol.StatusCodes.INTERNAL_ERROR;
                response.statusMessage = "Experiment execution error";
                response.success = false;
                response.error = e.getMessage();
                logger.error("Experiment execution error: {}", e.getMessage(), e);
            }
        }
        
        /**
         * Handle GET_STATUS request
         */
        private void handleGetStatus(NetworkProtocol.RequestMessage request,
                                    NetworkProtocol.ResponseMessage response) {
            // Placeholder for status checking logic
            response.statusCode = NetworkProtocol.StatusCodes.OK;
            response.statusMessage = "Status retrieved";
            response.success = true;
            response.data = new HashMap<>();
        }
        
        /**
         * Handle GET_MODELS request
         */
        private void handleGetModels(NetworkProtocol.RequestMessage request,
                                    NetworkProtocol.ResponseMessage response) {
            try {
                edu.utem.ftmk.masakgramprompt.dao.ReferenceDataDAO refDAO = new edu.utem.ftmk.masakgramprompt.dao.ReferenceDataDAO();
                List<Map<String, Object>> models = refDAO.getAllModels();
                
                response.statusCode = NetworkProtocol.StatusCodes.OK;
                response.statusMessage = "Models retrieved";
                response.success = true;
                response.data = models;
            } catch (Exception e) {
                response.statusCode = NetworkProtocol.StatusCodes.INTERNAL_ERROR;
                response.statusMessage = "Failed to retrieve models";
                response.success = false;
                response.error = e.getMessage();
            }
        }
        
        /**
         * Handle GET_TECHNIQUES request
         */
        private void handleGetTechniques(NetworkProtocol.RequestMessage request,
                                        NetworkProtocol.ResponseMessage response) {
            try {
                edu.utem.ftmk.masakgramprompt.dao.ReferenceDataDAO refDAO = new edu.utem.ftmk.masakgramprompt.dao.ReferenceDataDAO();
                List<Map<String, Object>> techniques = refDAO.getAllTechniques();
                
                response.statusCode = NetworkProtocol.StatusCodes.OK;
                response.statusMessage = "Techniques retrieved";
                response.success = true;
                response.data = techniques;
            } catch (Exception e) {
                response.statusCode = NetworkProtocol.StatusCodes.INTERNAL_ERROR;
                response.statusMessage = "Failed to retrieve techniques";
                response.success = false;
                response.error = e.getMessage();
            }
        }
        
        /**
         * Handle GET_TRANSCRIPTS request
         */
        private void handleGetTranscripts(NetworkProtocol.RequestMessage request,
                                         NetworkProtocol.ResponseMessage response) {
            try {
                edu.utem.ftmk.masakgramprompt.dao.ReferenceDataDAO refDAO = new edu.utem.ftmk.masakgramprompt.dao.ReferenceDataDAO();
                List<Map<String, Object>> transcripts = refDAO.getAllTranscripts();
                
                response.statusCode = NetworkProtocol.StatusCodes.OK;
                response.statusMessage = "Transcripts retrieved";
                response.success = true;
                response.data = transcripts;
            } catch (Exception e) {
                response.statusCode = NetworkProtocol.StatusCodes.INTERNAL_ERROR;
                response.statusMessage = "Failed to retrieve transcripts";
                response.success = false;
                response.error = e.getMessage();
            }
        }
        
        /**
         * Handle GET_DASHBOARD request
         */
        private void handleGetDashboard(NetworkProtocol.RequestMessage request,
                                       NetworkProtocol.ResponseMessage response) {
            try {
                edu.utem.ftmk.masakgramprompt.dao.ReferenceDataDAO refDAO = new edu.utem.ftmk.masakgramprompt.dao.ReferenceDataDAO();
                Map<String, Object> dashboard = refDAO.getDashboardSummary();
                
                response.statusCode = NetworkProtocol.StatusCodes.OK;
                response.statusMessage = "Dashboard data retrieved";
                response.success = true;
                response.data = dashboard;
            } catch (Exception e) {
                response.statusCode = NetworkProtocol.StatusCodes.INTERNAL_ERROR;
                response.statusMessage = "Failed to retrieve dashboard";
                response.success = false;
                response.error = e.getMessage();
            }
        }
        
        /**
         * Handle PING request (server heartbeat)
         */
        private void handlePing(NetworkProtocol.RequestMessage request,
                              NetworkProtocol.ResponseMessage response) {
            response.statusCode = NetworkProtocol.StatusCodes.OK;
            response.statusMessage = "Pong";
            response.success = true;
            response.data = new HashMap<String, String>() {{
                put("timestamp", String.valueOf(System.currentTimeMillis()));
                put("server", "MasakGramPrompt Server v2.0");
            }};
        }
        
        /**
         * Close client connection
         */
        private void closeConnection() {
            try {
                if (input != null) input.close();
                if (output != null) output.close();
                if (clientSocket != null && !clientSocket.isClosed()) {
                    clientSocket.close();
                }
                logger.info("Client connection closed");
            } catch (IOException e) {
                logger.error("Error closing connection: {}", e.getMessage());
            }
        }
    }
}