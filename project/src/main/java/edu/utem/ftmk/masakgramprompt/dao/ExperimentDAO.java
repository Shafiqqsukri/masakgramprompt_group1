package edu.utem.ftmk.masakgramprompt.dao;

import edu.utem.ftmk.masakgramprompt.db.DatabaseConnection;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * ExperimentDAO - full schema version. createIngredientResult() now
 * accepts and stores languageTag + isHallucinated (computed by
 * ExperimentService before calling this), fulfilling spec 5.4c.
 */
public class ExperimentDAO {
    private static final Logger logger = LoggerFactory.getLogger(ExperimentDAO.class);
    private DatabaseConnection dbConnection;

    public ExperimentDAO() {
        this.dbConnection = DatabaseConnection.getInstance();
    }

    public int createExperiment(int transcriptId, int modelId, int techniqueId) {
        String sql = "INSERT INTO experiment (transcript_id, model_id, technique_id, rag_enabled, status) " +
                     "VALUES (?, ?, ?, FALSE, 'pending')";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1, transcriptId);
            pstmt.setInt(2, modelId);
            pstmt.setInt(3, techniqueId);

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                logger.error("Failed to create experiment: no rows affected");
                return -1;
            }

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int experimentId = generatedKeys.getInt(1);
                    logger.info("Created experiment with ID: {}", experimentId);
                    return experimentId;
                }
            }
        } catch (SQLException e) {
            logger.error("Error creating experiment: {}", e.getMessage(), e);
        }
        return -1;
    }

    public boolean updateExperimentStatus(int experimentId, String status) {
        String sql = "UPDATE experiment SET status = ?, executed_at = CURRENT_TIMESTAMP WHERE experiment_id = ?";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, status);
            pstmt.setInt(2, experimentId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Error updating experiment status: {}", e.getMessage(), e);
            return false;
        }
    }

    public int createNutritionResult(int experimentId, String recipeName, int servings,
                                      JsonObject amountPerServing, JsonObject nutritionTotal,
                                      String rawJsonOutput, boolean jsonValid) {
        String sql = "INSERT INTO nutrition_result (" +
                "experiment_id, recipe_name, servings_estimated, " +
                "serving_calories, serving_total_fat_g, serving_saturated_fat_g, serving_cholesterol_mg, " +
                "serving_sodium_mg, serving_carbohydrate_g, serving_fiber_g, serving_sugars_g, " +
                "serving_protein_g, serving_vitamin_d_mcg, serving_calcium_mg, serving_iron_mg, serving_potassium_mg, " +
                "total_calories, total_fat_g, total_saturated_fat_g, total_cholesterol_mg, " +
                "total_sodium_mg, total_carbohydrate_g, total_fiber_g, total_sugars_g, " +
                "total_protein_g, total_vitamin_d_mcg, total_calcium_mg, total_iron_mg, total_potassium_mg, " +
                "raw_json_output, json_valid, created_at" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            int i = 1;
            pstmt.setInt(i++, experimentId);
            pstmt.setString(i++, recipeName);
            pstmt.setInt(i++, servings);

            setNullableFloat(pstmt, i++, getDouble(amountPerServing, "calories"));
            setNullableFloat(pstmt, i++, getDouble(amountPerServing, "total_fat_g"));
            setNullableFloat(pstmt, i++, getDouble(amountPerServing, "saturated_fat_g"));
            setNullableFloat(pstmt, i++, getDouble(amountPerServing, "cholesterol_mg"));
            setNullableFloat(pstmt, i++, getDouble(amountPerServing, "sodium_mg"));
            setNullableFloat(pstmt, i++, getDouble(amountPerServing, "total_carbohydrate_g"));
            setNullableFloat(pstmt, i++, getDouble(amountPerServing, "dietary_fiber_g"));
            setNullableFloat(pstmt, i++, getDouble(amountPerServing, "total_sugars_g"));
            setNullableFloat(pstmt, i++, getDouble(amountPerServing, "protein_g"));
            setNullableFloat(pstmt, i++, getDouble(amountPerServing, "vitamin_d_mcg"));
            setNullableFloat(pstmt, i++, getDouble(amountPerServing, "calcium_mg"));
            setNullableFloat(pstmt, i++, getDouble(amountPerServing, "iron_mg"));
            setNullableFloat(pstmt, i++, getDouble(amountPerServing, "potassium_mg"));

            setNullableFloat(pstmt, i++, getDouble(nutritionTotal, "calories"));
            setNullableFloat(pstmt, i++, getDouble(nutritionTotal, "total_fat_g"));
            setNullableFloat(pstmt, i++, getDouble(nutritionTotal, "saturated_fat_g"));
            setNullableFloat(pstmt, i++, getDouble(nutritionTotal, "cholesterol_mg"));
            setNullableFloat(pstmt, i++, getDouble(nutritionTotal, "sodium_mg"));
            setNullableFloat(pstmt, i++, getDouble(nutritionTotal, "total_carbohydrate_g"));
            setNullableFloat(pstmt, i++, getDouble(nutritionTotal, "dietary_fiber_g"));
            setNullableFloat(pstmt, i++, getDouble(nutritionTotal, "total_sugars_g"));
            setNullableFloat(pstmt, i++, getDouble(nutritionTotal, "protein_g"));
            setNullableFloat(pstmt, i++, getDouble(nutritionTotal, "vitamin_d_mcg"));
            setNullableFloat(pstmt, i++, getDouble(nutritionTotal, "calcium_mg"));
            setNullableFloat(pstmt, i++, getDouble(nutritionTotal, "iron_mg"));
            setNullableFloat(pstmt, i++, getDouble(nutritionTotal, "potassium_mg"));

            pstmt.setString(i++, rawJsonOutput);
            pstmt.setBoolean(i++, jsonValid);

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                logger.error("Failed to create nutrition result: no rows affected");
                return -1;
            }

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int resultId = generatedKeys.getInt(1);
                    logger.info("Created nutrition result with ID: {}", resultId);
                    return resultId;
                }
            }
        } catch (SQLException e) {
            logger.error("Error creating nutrition result: {}", e.getMessage(), e);
        }
        return -1;
    }

    /**
     * Create ingredient result - now includes language_tag and
     * is_hallucinated (computed upstream by ExperimentService via a
     * name-matching heuristic against ground truth).
     */
    public boolean createIngredientResult(int resultId, JsonObject ingredient,
                                           String languageTag, boolean isHallucinated) {
        String sql = "INSERT INTO ingredient_result (" +
                "result_id, name_original, name_en, quantity_value, unit_original, unit_en, estimated_weight_g, " +
                "calories, total_fat_g, saturated_fat_g, cholesterol_mg, sodium_mg, " +
                "total_carbohydrate_g, dietary_fiber_g, total_sugars_g, protein_g, " +
                "vitamin_d_mcg, calcium_mg, iron_mg, potassium_mg, language_tag, is_hallucinated" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            int i = 1;
            pstmt.setInt(i++, resultId);
            pstmt.setString(i++, getString(ingredient, "ingredient_name_original"));
            pstmt.setString(i++, getString(ingredient, "ingredient_name_en"));
            setNullableFloat(pstmt, i++, getDouble(ingredient, "quantity_value"));
            pstmt.setString(i++, getString(ingredient, "quantity_unit_original"));
            pstmt.setString(i++, getString(ingredient, "quantity_unit_en"));
            setNullableFloat(pstmt, i++, getDouble(ingredient, "estimated_weight_g"));
            setNullableFloat(pstmt, i++, getDouble(ingredient, "calories"));
            setNullableFloat(pstmt, i++, getDouble(ingredient, "total_fat_g"));
            setNullableFloat(pstmt, i++, getDouble(ingredient, "saturated_fat_g"));
            setNullableFloat(pstmt, i++, getDouble(ingredient, "cholesterol_mg"));
            setNullableFloat(pstmt, i++, getDouble(ingredient, "sodium_mg"));
            setNullableFloat(pstmt, i++, getDouble(ingredient, "total_carbohydrate_g"));
            setNullableFloat(pstmt, i++, getDouble(ingredient, "dietary_fiber_g"));
            setNullableFloat(pstmt, i++, getDouble(ingredient, "total_sugars_g"));
            setNullableFloat(pstmt, i++, getDouble(ingredient, "protein_g"));
            setNullableFloat(pstmt, i++, getDouble(ingredient, "vitamin_d_mcg"));
            setNullableFloat(pstmt, i++, getDouble(ingredient, "calcium_mg"));
            setNullableFloat(pstmt, i++, getDouble(ingredient, "iron_mg"));
            setNullableFloat(pstmt, i++, getDouble(ingredient, "potassium_mg"));
            pstmt.setString(i++, languageTag);
            pstmt.setBoolean(i++, isHallucinated);

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Error creating ingredient result: {}", e.getMessage(), e);
            return false;
        }
    }

    private Double getDouble(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return null;
        try {
            JsonElement el = obj.get(key);
            if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) return el.getAsDouble();
            return Double.parseDouble(el.getAsString());
        } catch (Exception e) { return null; }
    }

    private String getString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return null;
        try { return obj.get(key).getAsString(); } catch (Exception e) { return null; }
    }

    private void setNullableFloat(PreparedStatement pstmt, int index, Double value) throws SQLException {
        if (value == null) pstmt.setNull(index, Types.FLOAT);
        else pstmt.setFloat(index, value.floatValue());
    }

    public Map<String, Object> getExperiment(int experimentId) {
        String sql = "SELECT e.*, t.transcript_id, lm.model_name, pt.technique_name " +
                     "FROM experiment e " +
                     "JOIN transcript t ON e.transcript_id = t.transcript_id " +
                     "JOIN llm_model lm ON e.model_id = lm.model_id " +
                     "JOIN prompt_technique pt ON e.technique_id = pt.technique_id " +
                     "WHERE e.experiment_id = ?";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, experimentId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> experiment = new HashMap<>();
                    experiment.put("experimentId", rs.getInt("experiment_id"));
                    experiment.put("transcriptId", rs.getInt("transcript_id"));
                    experiment.put("modelName", rs.getString("model_name"));
                    experiment.put("technique", rs.getString("technique_name"));
                    experiment.put("status", rs.getString("status"));
                    experiment.put("ragEnabled", rs.getBoolean("rag_enabled"));
                    return experiment;
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching experiment: {}", e.getMessage(), e);
        }
        return null;
    }

    public List<Map<String, Object>> getExperimentsByTranscript(int transcriptId) {
        String sql = "SELECT e.*, lm.model_name, pt.technique_name " +
                     "FROM experiment e " +
                     "JOIN llm_model lm ON e.model_id = lm.model_id " +
                     "JOIN prompt_technique pt ON e.technique_id = pt.technique_id " +
                     "WHERE e.transcript_id = ? " +
                     "ORDER BY e.experiment_id";

        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, transcriptId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> experiment = new HashMap<>();
                    experiment.put("experimentId", rs.getInt("experiment_id"));
                    experiment.put("modelName", rs.getString("model_name"));
                    experiment.put("technique", rs.getString("technique_name"));
                    experiment.put("status", rs.getString("status"));
                    results.add(experiment);
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching experiments: {}", e.getMessage(), e);
        }
        return results;
    }

    public Map<String, Object> getNutritionResult(int resultId) {
        String sql = "SELECT * FROM nutrition_result WHERE result_id = ?";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, resultId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> result = new HashMap<>();
                    result.put("resultId", rs.getInt("result_id"));
                    result.put("experimentId", rs.getInt("experiment_id"));
                    result.put("recipeName", rs.getString("recipe_name"));
                    result.put("servingsEstimated", rs.getInt("servings_estimated"));
                    result.put("totalCalories", rs.getFloat("total_calories"));
                    result.put("rawJsonOutput", rs.getString("raw_json_output"));
                    result.put("jsonValid", rs.getBoolean("json_valid"));
                    return result;
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching nutrition result: {}", e.getMessage(), e);
        }
        return null;
    }
}