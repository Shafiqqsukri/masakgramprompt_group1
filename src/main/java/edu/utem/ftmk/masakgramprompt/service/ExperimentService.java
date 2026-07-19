package edu.utem.ftmk.masakgramprompt.service;

import edu.utem.ftmk.masakgramprompt.dao.ExperimentDAO;
import edu.utem.ftmk.masakgramprompt.dao.ReferenceDataDAO;
import edu.utem.ftmk.masakgramprompt.dao.ReportingDAO;
import edu.utem.ftmk.masakgramprompt.service.LlmService;   // your simple LLM service
import edu.utem.ftmk.masakgramprompt.network.NetworkProtocol;

import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * ExperimentService - orchestrates experiment execution.
 */
public class ExperimentService {
    private static final Logger logger = LoggerFactory.getLogger(ExperimentService.class);

    private ExperimentDAO experimentDAO;
    private ReferenceDataDAO referenceDataDAO;
    private ReportingDAO reportingDAO;
    private LlmService llmService;          // <-- properly declared
    private PromptService promptService;

    private final Map<String, Map<String, String>> promptCache = new ConcurrentHashMap<>();

    // Malay marker words for language heuristic
    private static final Set<String> MALAY_MARKERS = Set.of(
        "dan", "dengan", "yang", "ke", "di", "sikit", "sudu", "cawan", "secubit",
        "garam", "gula", "minyak", "goreng", "masak", "bawang", "cili", "santan",
        "tepung", "telur", "susu", "air", "kicap", "halia", "bawang putih", "sudu besar",
        "sudu kecil", "segenggam", "seketul", "sebiji", "beberapa"
    );
    private static final Pattern TAMIL_SCRIPT = Pattern.compile("[\\u0B80-\\u0BFF]");
    private static final Pattern CHINESE_SCRIPT = Pattern.compile("[\\u4E00-\\u9FFF]");

    @FunctionalInterface
    public interface StatusCallback {
        void updateStatus(int experimentId, String modelName, String technique,
                         String status, int progress, String message);
    }

    public ExperimentService() {
        this.experimentDAO = new ExperimentDAO();
        this.referenceDataDAO = new ReferenceDataDAO();
        this.reportingDAO = new ReportingDAO();
        this.llmService = new LlmService();      // <-- use correct class name
        this.promptService = new PromptService();
    }

    public NetworkProtocol.ExperimentResult runExperiment(
            int transcriptId, String modelName, String technique, StatusCallback callback) {

        try {
            logger.info("Starting experiment: transcript={}, model={}, technique={}",
                       transcriptId, modelName, technique);

            callback.updateStatus(0, modelName, technique, "pending", 10, "Looking up LLM model");
            Map<String, Object> modelData = referenceDataDAO.getModelByName(modelName);
            if (modelData == null) {
                callback.updateStatus(0, modelName, technique, "failed", 0, "Model not found");
                return null;
            }
            Integer modelId = ((Number) modelData.get("modelId")).intValue();
            String modelTag = (String) modelData.get("modelTag");

            callback.updateStatus(0, modelName, technique, "pending", 20, "Looking up prompt technique");
            Map<String, Object> techniqueData = referenceDataDAO.getTechniqueByName(technique);
            if (techniqueData == null) {
                callback.updateStatus(0, modelName, technique, "failed", 0, "Technique not found");
                return null;
            }
            Integer techniqueId = ((Number) techniqueData.get("techniqueId")).intValue();

            callback.updateStatus(0, modelName, technique, "pending", 30, "Loading transcript");
            Map<String, Object> transcriptData = referenceDataDAO.getTranscriptById(transcriptId);
            if (transcriptData == null) {
                callback.updateStatus(0, modelName, technique, "failed", 0, "Transcript not found");
                return null;
            }
            String transcriptPath = (String) transcriptData.get("filePath");

            callback.updateStatus(0, modelName, technique, "running", 40, "Creating experiment record");
            Integer experimentId = experimentDAO.createExperiment(transcriptId, modelId, techniqueId);
            if (experimentId == null || experimentId < 0) {
                callback.updateStatus(0, modelName, technique, "failed", 0, "Failed to create experiment");
                return null;
            }
            logger.info("Created experiment: id={}", experimentId);

            callback.updateStatus(experimentId, modelName, technique, "running", 50, "Loading prompts");
            String systemPromptFile = (String) techniqueData.get("systemPromptFile");
            String userPromptFile = (String) techniqueData.get("userPromptFile");
            String systemPrompt = loadPromptFile(systemPromptFile);
            String userPromptTemplate = loadPromptFile(userPromptFile);

            if (systemPrompt == null || userPromptTemplate == null) {
                experimentDAO.updateExperimentStatus(experimentId, "failed");
                callback.updateStatus(experimentId, modelName, technique, "failed", 0, "Prompt files not found");
                return null;
            }

            callback.updateStatus(experimentId, modelName, technique, "running", 60, "Reading transcript content");
            String transcriptContent = readFileContent(transcriptPath);
            if (transcriptContent == null) {
                experimentDAO.updateExperimentStatus(experimentId, "failed");
                callback.updateStatus(experimentId, modelName, technique, "failed", 0, "Transcript file not readable");
                return null;
            }

            callback.updateStatus(experimentId, modelName, technique, "running", 70, "Preparing prompt");
            String userPrompt = promptService.injectTranscript(userPromptTemplate, transcriptContent);

            callback.updateStatus(experimentId, modelName, technique, "running", 75, "Sending to LLM");
            String llmResponse = llmService.prompt(modelTag, systemPrompt, userPrompt);

            if (llmResponse == null || llmResponse.isEmpty()) {
                experimentDAO.updateExperimentStatus(experimentId, "failed");
                callback.updateStatus(experimentId, modelName, technique, "failed", 0, "LLM returned empty response");
                return null;
            }

            callback.updateStatus(experimentId, modelName, technique, "running", 85, "Parsing LLM response");
            String jsonString = extractJsonFromResponse(llmResponse);
            boolean jsonValid = jsonString != null && !jsonString.isEmpty();

            String recipeName = "Unknown Recipe";
            Integer servings = 0;
            JsonObject amountPerServing = new JsonObject();
            JsonObject nutritionTotal = new JsonObject();
            JsonArray ingredients = new JsonArray();

            if (jsonValid) {
                try {
                    JsonObject jsonData = JsonParser.parseString(jsonString).getAsJsonObject();
                    recipeName = jsonData.has("recipe_name") && !jsonData.get("recipe_name").isJsonNull() ?
                        jsonData.get("recipe_name").getAsString() : "Unknown Recipe";
                    servings = jsonData.has("servings_estimated") && !jsonData.get("servings_estimated").isJsonNull() ?
                        jsonData.get("servings_estimated").getAsInt() : 0;
                    if (jsonData.has("amount_per_serving") && jsonData.get("amount_per_serving").isJsonObject())
                        amountPerServing = jsonData.getAsJsonObject("amount_per_serving");
                    if (jsonData.has("nutrition_total") && jsonData.get("nutrition_total").isJsonObject())
                        nutritionTotal = jsonData.getAsJsonObject("nutrition_total");
                    if (jsonData.has("ingredients") && jsonData.get("ingredients").isJsonArray())
                        ingredients = jsonData.getAsJsonArray("ingredients");
                    logger.info("Parsed LLM JSON: recipe={}, ingredients={}", recipeName, ingredients.size());
                } catch (JsonSyntaxException | IllegalStateException e) {
                    logger.warn("Failed to parse JSON: {}", e.getMessage());
                    jsonValid = false;
                }
            }

            callback.updateStatus(experimentId, modelName, technique, "running", 90, "Storing results");
            Integer resultId = experimentDAO.createNutritionResult(
                experimentId, recipeName, servings, amountPerServing, nutritionTotal, llmResponse, jsonValid
            );

            if (resultId == null || resultId < 0) {
                experimentDAO.updateExperimentStatus(experimentId, "failed");
                callback.updateStatus(experimentId, modelName, technique, "failed", 0, "Failed to store results");
                return null;
            }

            if (jsonValid && ingredients.size() > 0) {
                storeIngredientResultsWithEvaluation(resultId, ingredients, transcriptId);
            }

            callback.updateStatus(experimentId, modelName, technique, "completed", 100, "Experiment complete");
            experimentDAO.updateExperimentStatus(experimentId, "completed");

            NetworkProtocol.ExperimentResult result = new NetworkProtocol.ExperimentResult();
            result.experimentId = experimentId;
            result.transcriptId = transcriptId;
            result.modelName = modelName;
            result.technique = technique;
            result.recipeName = recipeName;
            result.servingsEstimated = servings;
            result.amountPerServing = amountPerServing;
            result.nutritionTotal = nutritionTotal;
            result.ingredients = ingredients;
            result.rawJsonOutput = llmResponse;
            result.jsonValid = jsonValid;

            logger.info("Experiment completed successfully: id={}, recipe={}", experimentId, recipeName);
            return result;

        } catch (Exception e) {
            logger.error("Experiment execution error: {}", e.getMessage(), e);
            try {
                if (callback != null) {
                    callback.updateStatus(0, modelName, technique, "failed", 0, "Error: " + e.getMessage());
                }
            } catch (Exception ce) {
                logger.error("Error calling status callback", ce);
            }
            return null;
        }
    }

    // ---------- helper methods (unchanged) ----------
    private void storeIngredientResultsWithEvaluation(Integer resultId, JsonArray ingredientsArray, int transcriptId) {
        List<String> gtNames = reportingDAO.getGroundTruthNamesForTranscript(transcriptId);
        int stored = 0;
        for (JsonElement element : ingredientsArray) {
            if (!element.isJsonObject()) continue;
            JsonObject ingredient = element.getAsJsonObject();
            String nameOriginal = safeGetString(ingredient, "ingredient_name_original");
            String nameEn = safeGetString(ingredient, "ingredient_name_en");
            String languageTag = detectLanguageTag(nameOriginal);
            boolean isHallucinated = !gtNames.isEmpty() && !hasGroundTruthMatch(nameEn, nameOriginal, gtNames);
            boolean ok = experimentDAO.createIngredientResult(resultId, ingredient, languageTag, isHallucinated);
            if (ok) stored++;
        }
        logger.info("Stored {}/{} ingredients for result {} (ground truth available: {})",
                   stored, ingredientsArray.size(), resultId, !gtNames.isEmpty());
    }

    private boolean hasGroundTruthMatch(String nameEn, String nameOriginal, List<String> gtNamesLower) {
        String a = nameEn != null ? nameEn.toLowerCase().trim() : "";
        String b = nameOriginal != null ? nameOriginal.toLowerCase().trim() : "";
        if (a.isEmpty() && b.isEmpty()) return false;
        for (String gt : gtNamesLower) {
            if (gt.isEmpty()) continue;
            if ((!a.isEmpty() && (gt.contains(a) || a.contains(gt))) ||
                (!b.isEmpty() && (gt.contains(b) || b.contains(gt)))) {
                return true;
            }
        }
        return false;
    }

    private String detectLanguageTag(String text) {
        if (text == null || text.isBlank()) return "OT";
        if (TAMIL_SCRIPT.matcher(text).find() || CHINESE_SCRIPT.matcher(text).find()) return "OT";
        String lower = text.toLowerCase();
        for (String marker : MALAY_MARKERS) {
            if (lower.contains(marker)) return "MY";
        }
        if (text.matches("^[\\x00-\\x7F]*$")) return "EN";
        return "OT";
    }

    private String safeGetString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return "";
        try { return obj.get(key).getAsString(); } catch (Exception e) { return ""; }
    }

    private String loadPromptFile(String fileName) {
        if (fileName == null || fileName.isEmpty()) return null;
        String cleanFileName = fileName.startsWith("prompts/") ? fileName.substring(8) : fileName;
        String[] possiblePaths = {
            "src/main/resources/prompts/" + cleanFileName,
            "prompts/" + cleanFileName,
            cleanFileName
        };
        for (String path : possiblePaths) {
            File f = new File(path);
            if (f.exists()) return readFileContent(path);
        }
        logger.error("Prompt file not found: {}", fileName);
        return null;
    }

    private String readFileContent(String filePath) {
        try {
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
                String line;
                while ((line = reader.readLine()) != null) content.append(line).append("\n");
            }
            return content.toString().trim();
        } catch (IOException e) {
            logger.error("Failed to read file: {}", filePath, e);
            return null;
        }
    }

    private String extractJsonFromResponse(String response) {
        if (response == null || response.isEmpty()) return null;
        if (response.contains("```json")) {
            int start = response.indexOf("```json") + 7;
            int end = response.indexOf("```", start);
            if (end > start) return response.substring(start, end).trim();
        }
        if (response.contains("```")) {
            int start = response.indexOf("```") + 3;
            int end = response.indexOf("```", start);
            if (end > start) return response.substring(start, end).trim();
        }
        try { JsonParser.parseString(response); return response; }
        catch (JsonSyntaxException e) { logger.debug("Could not extract JSON from response"); }
        return null;
    }

    public void shutdown() {
        logger.info("Shutting down ExperimentService");
        promptCache.clear();
    }
}