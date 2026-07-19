package edu.utem.ftmk.masakgramprompt.client;

import edu.utem.ftmk.masakgramprompt.network.NetworkProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Batch Processor - RECONNECT PER EXPERIMENT VERSION
 * Opens a NEW connection for each experiment (like the proven working single-client test).
 * This isolates failures: one bad/slow experiment cannot hang or break the rest of the batch.
 *
 * FIXED: now loads actual transcript IDs from the database instead of assuming IDs 1..50.
 */
public class BatchExperimentProcessor {
    
    private final String host;
    private final int port;
    
    private List<Map<String, Object>> models;
    private List<Map<String, Object>> techniques;
    private List<Integer> transcriptIds;   // actual IDs from DB
    
    private int totalExperiments = 0;
    private int completedExperiments = 0;
    private int failedExperiments = 0;
    private int timeoutExperiments = 0;
    private long startTime;
    
    public BatchExperimentProcessor(String host, int port) {
        this.host = host;
        this.port = port;
    }
    
    /**
     * Load models/techniques and transcript IDs once using a throwaway connection
     */
    public boolean initialize() {
        System.out.println("\n=== Initializing Batch Processor ===");
        
        MasakGramPromptClient setupClient = new MasakGramPromptClient(host, port);
        if (!setupClient.connect()) {
            System.err.println("Failed to connect to server");
            return false;
        }
        
        try {
            this.models = setupClient.getModels();
            System.out.println("Loaded " + models.size() + " LLM models");
            
            this.techniques = setupClient.getTechniques();
            System.out.println("Loaded " + techniques.size() + " prompt techniques");
            
            // ---- FETCH ACTUAL TRANSCRIPT IDs ----
            this.transcriptIds = fetchTranscriptIds(setupClient);
            System.out.println("Loaded " + transcriptIds.size() + " transcripts");
            
        } catch (Exception e) {
            System.err.println("Error loading reference data: " + e.getMessage());
            return false;
        } finally {
            setupClient.disconnect();
        }
        
        this.totalExperiments = transcriptIds.size() * models.size() * techniques.size();
        
        System.out.println("\nTOTAL EXPERIMENTS TO RUN: " + totalExperiments);
        System.out.println("  = " + transcriptIds.size() + " transcripts x " + models.size() + " models x " 
                          + techniques.size() + " techniques");
        System.out.println("  Mode: fresh connection per experiment (isolated failures)");
        
        return true;
    }
    
    /**
     * Fetch transcript IDs from the server. The server returns all transcripts via GET_TRANSCRIPTS.
     */
    private List<Integer> fetchTranscriptIds(MasakGramPromptClient client) {
        List<Integer> ids = new ArrayList<>();
        try {
            List<Map<String, Object>> transcripts = client.getTranscripts();
            for (Map<String, Object> t : transcripts) {
                Object idObj = t.get("transcriptId");
                if (idObj instanceof Number) {
                    ids.add(((Number) idObj).intValue());
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch transcript IDs: " + e.getMessage());
        }
        // If for some reason we got none, fallback to a safe default (but warn)
        if (ids.isEmpty()) {
            System.err.println("WARNING: No transcript IDs returned. Using fallback IDs 1..50 (may fail).");
            for (int i = 1; i <= 50; i++) ids.add(i);
        }
        return ids;
    }
    
    public void runAllExperiments() {
        startTime = System.currentTimeMillis();
        System.out.println("\n=== Starting Batch Processing ===");
        System.out.println("Started at: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        System.out.println();
        
        int experimentNumber = 0;
        
        for (int transcriptId : transcriptIds) {
            System.out.println("--- Transcript " + transcriptId + " ---");
            
            for (Map<String, Object> model : models) {
                String modelName = (String) model.get("modelName");
                
                for (Map<String, Object> technique : techniques) {
                    String techniqueName = (String) technique.get("techniqueName");
                    
                    experimentNumber++;
                    
                    runSingleExperimentIsolated(experimentNumber, transcriptId, modelName, techniqueName);
                    
                    if (experimentNumber % 20 == 0) {
                        printProgress(experimentNumber);
                    }
                }
            }
        }
        
        printFinalSummary();
    }
    
    /**
     * Run ONE experiment with its own fresh connection.
     * Connect -> run -> disconnect. Failures here cannot affect other experiments.
     */
    private void runSingleExperimentIsolated(int experimentNumber, int transcriptId,
                                              String modelName, String techniqueName) {
        System.out.printf("[%4d/%4d] T=%2d | M=%-22s | Tech=%-18s ... ",
                experimentNumber, totalExperiments, transcriptId,
                modelName.length() > 22 ? modelName.substring(0, 22) : modelName,
                techniqueName);
        System.out.flush();
        
        MasakGramPromptClient client = new MasakGramPromptClient(host, port);
        
        try {
            if (!client.connect()) {
                System.out.println("FAILED (could not connect)");
                failedExperiments++;
                return;
            }
            
            NetworkProtocol.ExperimentResult result =
                    client.runExperiment(transcriptId, modelName, techniqueName);
            
            if (result != null && result.experimentId > 0) {
                System.out.println("OK id=" + result.experimentId);
                completedExperiments++;
            } else {
                System.out.println("FAILED (server returned no result / timeout)");
                failedExperiments++;
            }
            
        } catch (Exception e) {
            System.out.println("ERROR (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
            failedExperiments++;
        } finally {
            client.disconnect();
        }
    }
    
    private void printProgress(int currentExp) {
        long elapsed = System.currentTimeMillis() - startTime;
        double rate = (currentExp * 1000.0) / Math.max(elapsed, 1);
        long remaining = (long) ((totalExperiments - currentExp) / Math.max(rate, 0.001));
        
        System.out.println("\n--- PROGRESS ---");
        System.out.println("Processed: " + currentExp + "/" + totalExperiments);
        System.out.println("OK: " + completedExperiments + "  Failed: " + failedExperiments);
        System.out.println("Elapsed: " + formatTime(elapsed / 1000) + "  ETA: " + formatTime(remaining));
        System.out.println();
    }
    
    private void printFinalSummary() {
        long totalTime = System.currentTimeMillis() - startTime;
        
        System.out.println("\n==========================================");
        System.out.println("     BATCH PROCESSING COMPLETE");
        System.out.println("==========================================");
        System.out.println("Total: " + totalExperiments);
        System.out.println("OK: " + completedExperiments);
        System.out.println("Failed: " + failedExperiments);
        System.out.println("Success Rate: " + String.format("%.2f%%",
                (completedExperiments * 100.0) / Math.max(totalExperiments, 1)));
        System.out.println("Total Time: " + formatTime(totalTime / 1000));
        System.out.println("Finished at: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }
    
    private String formatTime(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }
    
    public static void main(String[] args) {
        String serverHost = "localhost";
        int serverPort = 5555;
        
        if (args.length >= 2) {
            serverHost = args[0];
            serverPort = Integer.parseInt(args[1]);
        }
        
        System.out.println("==========================================");
        System.out.println("  MasakGramPrompt Batch Processor");
        System.out.println("  (Reconnect-per-experiment - isolated)");
        System.out.println("==========================================");
        
        BatchExperimentProcessor processor =
                new BatchExperimentProcessor(serverHost, serverPort);
        
        if (!processor.initialize()) {
            System.exit(1);
        }
        
        System.out.println("\nThis will take a while. Continue? (yes/no)");
        Scanner scanner = new Scanner(System.in);
        String response = scanner.nextLine().trim().toLowerCase();
        
        if (!response.equals("yes")) {
            System.out.println("Cancelled.");
            System.exit(0);
        }
        
        processor.runAllExperiments();
        System.exit(0);
    }
}