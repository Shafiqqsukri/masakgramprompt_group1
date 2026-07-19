package edu.utem.ftmk.masakgramprompt.dao;

import edu.utem.ftmk.masakgramprompt.db.DatabaseConnection;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ReportingDAO - powers the Nutritional Fact Sheet (5.4), the Reel
 * Analysis Dashboard (5.3), and the 10 CSV exports (5.5).
 */
public class ReportingDAO {
    private static final Logger logger = LoggerFactory.getLogger(ReportingDAO.class);
    private final DatabaseConnection dbConnection;

    private static final Map<String, String> EXPORT_QUERIES = new LinkedHashMap<>();
    static {
        EXPORT_QUERIES.put("layer1a_exact_match", "SELECT e.experiment_id, t.transcript_id AS video_id, m.model_name, pt.technique_name, e.rag_enabled, "
            + "gti.name_original AS gt_name_original, gti.name_en AS gt_name_en, "
            + "gti.quantity_unit_culinary AS gt_unit, "
            + "ir.name_original AS pred_name_original, ir.name_en AS pred_name_en, "
            + "ir.unit_original AS pred_unit "
            + "FROM experiment e "
            + "JOIN transcript t ON e.transcript_id = t.transcript_id "
            + "JOIN llm_model m ON e.model_id = m.model_id "
            + "JOIN prompt_technique pt ON e.technique_id = pt.technique_id "
            + "JOIN nutrition_result nr ON e.experiment_id = nr.experiment_id "
            + "JOIN ingredient_result ir ON nr.result_id = ir.result_id "
            + "JOIN ground_truth_reel gtr ON t.transcript_id = gtr.transcript_id "
            + "JOIN ground_truth_ingredient gti ON gtr.gt_reel_id = gti.gt_reel_id "
            + "WHERE e.status = 'completed' "
            + "ORDER BY e.experiment_id, gti.gt_ingredient_id ");
        EXPORT_QUERIES.put("layer1b_text_similarity", "SELECT e.experiment_id, t.transcript_id AS video_id, m.model_name, pt.technique_name, e.rag_enabled, "
            + "gti.name_original AS gt_name_original, gti.name_en AS gt_name_en, "
            + "ir.name_original AS pred_name_original, ir.name_en AS pred_name_en "
            + "FROM experiment e "
            + "JOIN transcript t ON e.transcript_id = t.transcript_id "
            + "JOIN llm_model m ON e.model_id = m.model_id "
            + "JOIN prompt_technique pt ON e.technique_id = pt.technique_id "
            + "JOIN nutrition_result nr ON e.experiment_id = nr.experiment_id "
            + "JOIN ingredient_result ir ON nr.result_id = ir.result_id "
            + "JOIN ground_truth_reel gtr ON t.transcript_id = gtr.transcript_id "
            + "JOIN ground_truth_ingredient gti ON gtr.gt_reel_id = gti.gt_reel_id "
            + "WHERE e.status = 'completed' "
            + "ORDER BY e.experiment_id, gti.gt_ingredient_id ");
        EXPORT_QUERIES.put("layer2a_numeric_quantity", "SELECT e.experiment_id, t.transcript_id AS video_id, m.model_name, pt.technique_name, e.rag_enabled, "
            + "gti.quantity_value_culinary AS gt_quantity_value, gti.estimated_weight_g AS gt_weight_g, "
            + "ir.quantity_value AS pred_quantity_value, ir.estimated_weight_g AS pred_weight_g "
            + "FROM experiment e "
            + "JOIN transcript t ON e.transcript_id = t.transcript_id "
            + "JOIN llm_model m ON e.model_id = m.model_id "
            + "JOIN prompt_technique pt ON e.technique_id = pt.technique_id "
            + "JOIN nutrition_result nr ON e.experiment_id = nr.experiment_id "
            + "JOIN ingredient_result ir ON nr.result_id = ir.result_id "
            + "JOIN ground_truth_reel gtr ON t.transcript_id = gtr.transcript_id "
            + "JOIN ground_truth_ingredient gti ON gtr.gt_reel_id = gti.gt_reel_id "
            + "WHERE e.status = 'completed' AND gti.annotation_layer = 'layer2' "
            + "ORDER BY e.experiment_id, gti.gt_ingredient_id ");
        EXPORT_QUERIES.put("layer2b_numeric_nutrition", "SELECT e.experiment_id, t.transcript_id AS video_id, m.model_name, pt.technique_name, e.rag_enabled, "
            + "gti.calories AS gt_calories, gti.protein_g AS gt_protein_g, "
            + "gti.total_fat_g AS gt_fat_g, gti.total_carbohydrate_g AS gt_carbohydrate_g, "
            + "ir.calories AS pred_calories, ir.protein_g AS pred_protein_g, "
            + "ir.total_fat_g AS pred_fat_g, ir.total_carbohydrate_g AS pred_carbohydrate_g "
            + "FROM experiment e "
            + "JOIN transcript t ON e.transcript_id = t.transcript_id "
            + "JOIN llm_model m ON e.model_id = m.model_id "
            + "JOIN prompt_technique pt ON e.technique_id = pt.technique_id "
            + "JOIN nutrition_result nr ON e.experiment_id = nr.experiment_id "
            + "JOIN ingredient_result ir ON nr.result_id = ir.result_id "
            + "JOIN ground_truth_reel gtr ON t.transcript_id = gtr.transcript_id "
            + "JOIN ground_truth_ingredient gti ON gtr.gt_reel_id = gti.gt_reel_id "
            + "WHERE e.status = 'completed' AND gti.annotation_layer = 'layer2' "
            + "ORDER BY e.experiment_id, gti.gt_ingredient_id ");
        EXPORT_QUERIES.put("layer2c_nutrition_totals", "SELECT e.experiment_id, t.transcript_id AS video_id, m.model_name, pt.technique_name, e.rag_enabled, "
            + "SUM(gti.calories) AS gt_total_calories, SUM(gti.protein_g) AS gt_total_protein_g, "
            + "SUM(gti.total_fat_g) AS gt_total_fat_g, SUM(gti.total_carbohydrate_g) AS gt_total_carbohydrate_g, "
            + "nr.total_calories AS pred_total_calories, nr.total_protein_g AS pred_total_protein_g, "
            + "nr.total_fat_g AS pred_total_fat_g, nr.total_carbohydrate_g AS pred_total_carbohydrate_g "
            + "FROM experiment e "
            + "JOIN transcript t ON e.transcript_id = t.transcript_id "
            + "JOIN llm_model m ON e.model_id = m.model_id "
            + "JOIN prompt_technique pt ON e.technique_id = pt.technique_id "
            + "JOIN nutrition_result nr ON e.experiment_id = nr.experiment_id "
            + "JOIN ground_truth_reel gtr ON t.transcript_id = gtr.transcript_id "
            + "JOIN ground_truth_ingredient gti ON gtr.gt_reel_id = gti.gt_reel_id "
            + "WHERE e.status = 'completed' AND gti.annotation_layer = 'layer2' "
            + "GROUP BY e.experiment_id, t.transcript_id, m.model_name, pt.technique_name, e.rag_enabled, "
            + "nr.total_calories, nr.total_protein_g, nr.total_fat_g, nr.total_carbohydrate_g "
            + "ORDER BY e.experiment_id ");
        EXPORT_QUERIES.put("layer3a_json_validity", "SELECT m.model_name, pt.technique_name, e.rag_enabled, "
            + "COUNT(*) AS total_runs, "
            + "SUM(CASE WHEN nr.json_valid = TRUE THEN 1 ELSE 0 END) AS valid_count, "
            + "SUM(CASE WHEN nr.json_valid = FALSE THEN 1 ELSE 0 END) AS invalid_count, "
            + "ROUND(SUM(CASE WHEN nr.json_valid = TRUE THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 2) AS validity_rate_pct "
            + "FROM experiment e "
            + "JOIN llm_model m ON e.model_id = m.model_id "
            + "JOIN prompt_technique pt ON e.technique_id = pt.technique_id "
            + "JOIN nutrition_result nr ON e.experiment_id = nr.experiment_id "
            + "WHERE e.status = 'completed' "
            + "GROUP BY m.model_name, pt.technique_name, e.rag_enabled "
            + "ORDER BY m.model_name, pt.technique_name ");
        EXPORT_QUERIES.put("layer3b_hallucination", "SELECT e.experiment_id, t.transcript_id AS video_id, m.model_name, pt.technique_name, e.rag_enabled, "
            + "ir.name_original AS pred_name_original, ir.name_en AS pred_name_en, ir.is_hallucinated "
            + "FROM experiment e "
            + "JOIN transcript t ON e.transcript_id = t.transcript_id "
            + "JOIN llm_model m ON e.model_id = m.model_id "
            + "JOIN prompt_technique pt ON e.technique_id = pt.technique_id "
            + "JOIN nutrition_result nr ON e.experiment_id = nr.experiment_id "
            + "JOIN ingredient_result ir ON nr.result_id = ir.result_id "
            + "WHERE e.status = 'completed' "
            + "ORDER BY e.experiment_id, ir.ingredient_id ");
        EXPORT_QUERIES.put("layer3c_ingredient_detection", "SELECT e.experiment_id, t.transcript_id AS video_id, m.model_name, pt.technique_name, e.rag_enabled, "
            + "COUNT(DISTINCT gti.gt_ingredient_id) AS gt_ingredient_count, "
            + "COUNT(DISTINCT ir.ingredient_id) AS pred_ingredient_count, "
            + "SUM(CASE WHEN ir.is_hallucinated = FALSE THEN 1 ELSE 0 END) AS true_positives, "
            + "SUM(CASE WHEN ir.is_hallucinated = TRUE THEN 1 ELSE 0 END) AS false_positives "
            + "FROM experiment e "
            + "JOIN transcript t ON e.transcript_id = t.transcript_id "
            + "JOIN llm_model m ON e.model_id = m.model_id "
            + "JOIN prompt_technique pt ON e.technique_id = pt.technique_id "
            + "JOIN nutrition_result nr ON e.experiment_id = nr.experiment_id "
            + "JOIN ingredient_result ir ON nr.result_id = ir.result_id "
            + "JOIN ground_truth_reel gtr ON t.transcript_id = gtr.transcript_id "
            + "JOIN ground_truth_ingredient gti ON gtr.gt_reel_id = gti.gt_reel_id "
            + "WHERE e.status = 'completed' "
            + "GROUP BY e.experiment_id, t.transcript_id, m.model_name, pt.technique_name, e.rag_enabled "
            + "ORDER BY e.experiment_id ");
        EXPORT_QUERIES.put("layer4_human_evaluation", "SELECT NULL AS evaluation_id, NULL AS result_id, NULL AS experiment_id, "
            + "NULL AS video_id, NULL AS model_name, NULL AS technique_name, "
            + "NULL AS annotator_id, NULL AS fluency_score, "
            + "NULL AS completeness_score, NULL AS plausibility_score, NULL AS evaluated_at "
            + "WHERE FALSE ");
        EXPORT_QUERIES.put("layer5_condition_scores", "SELECT t.transcript_id AS video_id, m.model_name, pt.technique_name, e.rag_enabled, "
            + "COUNT(DISTINCT ir.ingredient_id) AS pred_count, "
            + "SUM(CASE WHEN ir.is_hallucinated = FALSE THEN 1 ELSE 0 END) AS true_positives, "
            + "SUM(CASE WHEN ir.is_hallucinated = TRUE THEN 1 ELSE 0 END) AS false_positives, "
            + "COUNT(DISTINCT gti.gt_ingredient_id) AS gt_count, "
            + "nr.json_valid, nr.total_calories AS pred_total_calories, "
            + "SUM(gti.calories) AS gt_total_calories "
            + "FROM experiment e "
            + "JOIN transcript t ON e.transcript_id = t.transcript_id "
            + "JOIN llm_model m ON e.model_id = m.model_id "
            + "JOIN prompt_technique pt ON e.technique_id = pt.technique_id "
            + "JOIN nutrition_result nr ON e.experiment_id = nr.experiment_id "
            + "JOIN ingredient_result ir ON nr.result_id = ir.result_id "
            + "JOIN ground_truth_reel gtr ON t.transcript_id = gtr.transcript_id "
            + "JOIN ground_truth_ingredient gti ON gtr.gt_reel_id = gti.gt_reel_id "
            + "WHERE e.status = 'completed' AND gti.annotation_layer = 'layer2' "
            + "GROUP BY t.transcript_id, m.model_name, pt.technique_name, e.rag_enabled, nr.json_valid, nr.total_calories "
            + "ORDER BY t.transcript_id, m.model_name, pt.technique_name ");
    }

    public ReportingDAO() {
        this.dbConnection = DatabaseConnection.getInstance();
    }

    public boolean isValidLayer(String layer) { return EXPORT_QUERIES.containsKey(layer); }

    public String exportLayerAsCsv(String layer) {
        String sql = EXPORT_QUERIES.get(layer);
        if (sql == null) return null;
        return executeCsv(sql, null);
    }

    private String executeCsv(String sql, Integer paramExperimentId) {
        StringBuilder csv = new StringBuilder();
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (paramExperimentId != null) pstmt.setInt(1, paramExperimentId);
            try (ResultSet rs = pstmt.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int cols = meta.getColumnCount();
                for (int i = 1; i <= cols; i++) {
                    csv.append(csvEscape(meta.getColumnLabel(i)));
                    if (i < cols) csv.append(",");
                }
                csv.append("\n");
                while (rs.next()) {
                    for (int i = 1; i <= cols; i++) {
                        Object val = rs.getObject(i);
                        csv.append(csvEscape(val == null ? "" : val.toString()));
                        if (i < cols) csv.append(",");
                    }
                    csv.append("\n");
                }
            }
        } catch (SQLException e) {
            logger.error("Error executing CSV export: {}", e.getMessage(), e);
            return null;
        }
        return csv.toString();
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Fact sheet CSV for ONE experiment (spec 5.4e) - ground truth vs
     * predicted ingredients side by side with full field set.
     */
    public String exportFactSheetCsv(int experimentId) {
        JsonObject sheet = getFactSheet(experimentId);
        if (sheet == null) return null;

        StringBuilder csv = new StringBuilder();
        csv.append("side,name_original,name_en,quantity,unit,language_tag,is_hallucinated,calories,fat_g,protein_g,carbohydrate_g\n");

        if (sheet.has("groundTruthIngredients")) {
            for (var el : sheet.getAsJsonArray("groundTruthIngredients")) {
                JsonObject g = el.getAsJsonObject();
                csv.append("ground_truth,")
                   .append(csvEscape(getStr(g, "nameOriginal"))).append(",")
                   .append(csvEscape(getStr(g, "nameEn"))).append(",")
                   .append(getStr(g, "quantityValue")).append(",")
                   .append(csvEscape(getStr(g, "quantityUnit"))).append(",")
                   .append(csvEscape(getStr(g, "languageTag"))).append(",")
                   .append(",")
                   .append(getStr(g, "calories")).append(",")
                   .append(getStr(g, "totalFatG")).append(",")
                   .append(getStr(g, "proteinG")).append(",")
                   .append(getStr(g, "totalCarbohydrateG")).append("\n");
            }
        }
        if (sheet.has("predictedIngredients")) {
            for (var el : sheet.getAsJsonArray("predictedIngredients")) {
                JsonObject p = el.getAsJsonObject();
                csv.append("predicted,")
                   .append(csvEscape(getStr(p, "nameOriginal"))).append(",")
                   .append(csvEscape(getStr(p, "nameEn"))).append(",")
                   .append(getStr(p, "quantityValue")).append(",")
                   .append(csvEscape(getStr(p, "unitOriginal"))).append(",")
                   .append(csvEscape(getStr(p, "languageTag"))).append(",")
                   .append(getStr(p, "isHallucinated")).append(",")
                   .append(getStr(p, "calories")).append(",")
                   .append(getStr(p, "totalFatG")).append(",")
                   .append(getStr(p, "proteinG")).append(",")
                   .append(getStr(p, "totalCarbohydrateG")).append("\n");
            }
        }
        return csv.toString();
    }

    private String getStr(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) return "";
        return obj.get(key).getAsString();
    }

    /**
     * Ground truth ingredient names (lowercased, name_en with fallback
     * to name_original) for the reel linked to a transcript. Empty
     * list if no ground truth has been annotated yet.
     */
    public List<String> getGroundTruthNamesForTranscript(int transcriptId) {
        List<String> names = new ArrayList<>();
        String sql = "SELECT gti.name_en, gti.name_original "
            + "FROM ground_truth_reel gtr "
            + "JOIN ground_truth_ingredient gti ON gtr.gt_reel_id = gti.gt_reel_id "
            + "WHERE gtr.transcript_id = ? ";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, transcriptId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String nameEn = rs.getString("name_en");
                    String nameOriginal = rs.getString("name_original");
                    String chosen = (nameEn != null && !nameEn.isBlank()) ? nameEn : nameOriginal;
                    if (chosen != null) names.add(chosen.toLowerCase().trim());
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching ground truth names: {}", e.getMessage(), e);
        }
        return names;
    }

    /**
     * Nutritional Fact Sheet for one experiment (spec 5.4).
     */
    public JsonObject getFactSheet(int experimentId) {
        JsonObject sheet = new JsonObject();
        String headSql = "SELECT e.experiment_id, e.transcript_id, m.model_name, pt.technique_name, "
            + "nr.result_id, nr.recipe_name, nr.servings_estimated, nr.json_valid, "
            + "nr.serving_calories, nr.serving_total_fat_g, nr.serving_saturated_fat_g, "
            + "nr.serving_cholesterol_mg, nr.serving_sodium_mg, nr.serving_carbohydrate_g, "
            + "nr.serving_fiber_g, nr.serving_sugars_g, nr.serving_protein_g, "
            + "nr.serving_vitamin_d_mcg, nr.serving_calcium_mg, nr.serving_iron_mg, nr.serving_potassium_mg, "
            + "nr.total_calories, nr.total_fat_g, nr.total_saturated_fat_g, "
            + "nr.total_cholesterol_mg, nr.total_sodium_mg, nr.total_carbohydrate_g, "
            + "nr.total_fiber_g, nr.total_sugars_g, nr.total_protein_g, "
            + "nr.total_vitamin_d_mcg, nr.total_calcium_mg, nr.total_iron_mg, nr.total_potassium_mg "
            + "FROM experiment e "
            + "JOIN llm_model m ON e.model_id = m.model_id "
            + "JOIN prompt_technique pt ON e.technique_id = pt.technique_id "
            + "JOIN nutrition_result nr ON e.experiment_id = nr.experiment_id "
            + "WHERE e.experiment_id = ? ";

        Integer resultId = null;
        Integer transcriptId = null;

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(headSql)) {
            pstmt.setInt(1, experimentId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) return null;
                resultId = rs.getInt("result_id");
                transcriptId = rs.getInt("transcript_id");

                sheet.addProperty("experimentId", rs.getInt("experiment_id"));
                sheet.addProperty("transcriptId", transcriptId);
                sheet.addProperty("modelName", rs.getString("model_name"));
                sheet.addProperty("techniqueName", rs.getString("technique_name"));
                sheet.addProperty("recipeName", rs.getString("recipe_name"));
                sheet.addProperty("servingsEstimated", rs.getInt("servings_estimated"));
                sheet.addProperty("jsonValid", rs.getBoolean("json_valid"));

                JsonObject perServing = new JsonObject();
                perServing.addProperty("calories", rs.getFloat("serving_calories"));
                perServing.addProperty("total_fat_g", rs.getFloat("serving_total_fat_g"));
                perServing.addProperty("saturated_fat_g", rs.getFloat("serving_saturated_fat_g"));
                perServing.addProperty("cholesterol_mg", rs.getFloat("serving_cholesterol_mg"));
                perServing.addProperty("sodium_mg", rs.getFloat("serving_sodium_mg"));
                perServing.addProperty("total_carbohydrate_g", rs.getFloat("serving_carbohydrate_g"));
                perServing.addProperty("dietary_fiber_g", rs.getFloat("serving_fiber_g"));
                perServing.addProperty("total_sugars_g", rs.getFloat("serving_sugars_g"));
                perServing.addProperty("protein_g", rs.getFloat("serving_protein_g"));
                perServing.addProperty("vitamin_d_mcg", rs.getFloat("serving_vitamin_d_mcg"));
                perServing.addProperty("calcium_mg", rs.getFloat("serving_calcium_mg"));
                perServing.addProperty("iron_mg", rs.getFloat("serving_iron_mg"));
                perServing.addProperty("potassium_mg", rs.getFloat("serving_potassium_mg"));
                sheet.add("perServing", perServing);

                JsonObject total = new JsonObject();
                total.addProperty("calories", rs.getFloat("total_calories"));
                total.addProperty("total_fat_g", rs.getFloat("total_fat_g"));
                total.addProperty("saturated_fat_g", rs.getFloat("total_saturated_fat_g"));
                total.addProperty("cholesterol_mg", rs.getFloat("total_cholesterol_mg"));
                total.addProperty("sodium_mg", rs.getFloat("total_sodium_mg"));
                total.addProperty("total_carbohydrate_g", rs.getFloat("total_carbohydrate_g"));
                total.addProperty("dietary_fiber_g", rs.getFloat("total_fiber_g"));
                total.addProperty("total_sugars_g", rs.getFloat("total_sugars_g"));
                total.addProperty("protein_g", rs.getFloat("total_protein_g"));
                total.addProperty("vitamin_d_mcg", rs.getFloat("total_vitamin_d_mcg"));
                total.addProperty("calcium_mg", rs.getFloat("total_calcium_mg"));
                total.addProperty("iron_mg", rs.getFloat("total_iron_mg"));
                total.addProperty("potassium_mg", rs.getFloat("total_potassium_mg"));
                sheet.add("total", total);
            }
        } catch (SQLException e) {
            logger.error("Error building fact sheet head: {}", e.getMessage(), e);
            return null;
        }

        JsonArray predicted = new JsonArray();
        String predSql = "SELECT name_original, name_en, quantity_value, unit_original, unit_en, "
            + "estimated_weight_g, calories, total_fat_g, protein_g, total_carbohydrate_g, "
            + "sodium_mg, language_tag, is_hallucinated "
            + "FROM ingredient_result WHERE result_id = ? ORDER BY ingredient_id ";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(predSql)) {
            pstmt.setInt(1, resultId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    JsonObject ing = new JsonObject();
                    ing.addProperty("nameOriginal", rs.getString("name_original"));
                    ing.addProperty("nameEn", rs.getString("name_en"));
                    ing.addProperty("quantityValue", rs.getFloat("quantity_value"));
                    ing.addProperty("unitOriginal", rs.getString("unit_original"));
                    ing.addProperty("unitEn", rs.getString("unit_en"));
                    ing.addProperty("estimatedWeightG", rs.getFloat("estimated_weight_g"));
                    ing.addProperty("calories", rs.getFloat("calories"));
                    ing.addProperty("totalFatG", rs.getFloat("total_fat_g"));
                    ing.addProperty("proteinG", rs.getFloat("protein_g"));
                    ing.addProperty("totalCarbohydrateG", rs.getFloat("total_carbohydrate_g"));
                    ing.addProperty("sodiumMg", rs.getFloat("sodium_mg"));
                    ing.addProperty("languageTag", rs.getString("language_tag"));
                    ing.addProperty("isHallucinated", rs.getBoolean("is_hallucinated"));
                    predicted.add(ing);
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching predicted ingredients: {}", e.getMessage(), e);
        }
        sheet.add("predictedIngredients", predicted);

        JsonArray groundTruth = new JsonArray();
        String gtSql = "SELECT gti.name_original, gti.name_en, gti.quantity_expression, "
            + "gti.quantity_category, gti.quantity_unit_culinary, gti.quantity_value_culinary, "
            + "gti.estimated_weight_g, gti.calories, gti.total_fat_g, gti.protein_g, "
            + "gti.total_carbohydrate_g, gti.sodium_mg, gti.language_mentioned "
            + "FROM ground_truth_reel gtr "
            + "JOIN ground_truth_ingredient gti ON gtr.gt_reel_id = gti.gt_reel_id "
            + "WHERE gtr.transcript_id = ? ORDER BY gti.gt_ingredient_id ";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(gtSql)) {
            pstmt.setInt(1, transcriptId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    JsonObject ing = new JsonObject();
                    ing.addProperty("nameOriginal", rs.getString("name_original"));
                    ing.addProperty("nameEn", rs.getString("name_en"));
                    ing.addProperty("quantityExpression", rs.getString("quantity_expression"));
                    ing.addProperty("quantityCategory", rs.getString("quantity_category"));
                    ing.addProperty("quantityUnit", rs.getString("quantity_unit_culinary"));
                    ing.addProperty("quantityValue", rs.getFloat("quantity_value_culinary"));
                    ing.addProperty("estimatedWeightG", rs.getFloat("estimated_weight_g"));
                    ing.addProperty("calories", rs.getFloat("calories"));
                    ing.addProperty("totalFatG", rs.getFloat("total_fat_g"));
                    ing.addProperty("proteinG", rs.getFloat("protein_g"));
                    ing.addProperty("totalCarbohydrateG", rs.getFloat("total_carbohydrate_g"));
                    ing.addProperty("sodiumMg", rs.getFloat("sodium_mg"));
                    ing.addProperty("languageTag", rs.getString("language_mentioned"));
                    groundTruth.add(ing);
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching ground truth ingredients: {}", e.getMessage(), e);
        }
        sheet.add("groundTruthIngredients", groundTruth);
        sheet.addProperty("hasGroundTruth", groundTruth.size() > 0);

        return sheet;
    }

    /**
     * Reel Analysis Dashboard list (spec 5.3a): one row per reel with
     * influencer, Instagram ID, transcript verification status, ground
     * truth availability, and a heuristic language tag computed from
     * the actual transcript file content.
     *
     * NOTE: reel.duration_seconds requires ALTER_SCHEMA_V2.sql and is
     * not auto-populated (no video duration source is wired up yet) -
     * returns null/"N/A" until backfilled.
     */
    public JsonArray getReelDashboard() {
        JsonArray reels = new JsonArray();
        String sql = "SELECT r.reel_id, r.reel_id_instagram, r.reel_url, r.duration_seconds, "
            + "i.name AS influencer_name, i.instagram_account, "
            + "t.transcript_id, t.file_path, t.audio_transcript_consistent, t.verified_by_name, "
            + "(SELECT COUNT(*) FROM ground_truth_reel gtr WHERE gtr.transcript_id = t.transcript_id) AS gt_count "
            + "FROM reel r "
            + "JOIN influencer i ON r.influencer_id = i.influencer_id "
            + "LEFT JOIN transcript t ON t.reel_id = r.reel_id "
            + "ORDER BY r.reel_id ";
        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                JsonObject reel = new JsonObject();
                reel.addProperty("reelId", rs.getInt("reel_id"));
                reel.addProperty("reelIdInstagram", rs.getString("reel_id_instagram"));
                reel.addProperty("reelUrl", rs.getString("reel_url"));
                Integer duration = rs.getObject("duration_seconds", Integer.class);
                reel.addProperty("durationSeconds", duration);
                reel.addProperty("influencerName", rs.getString("influencer_name"));
                reel.addProperty("instagramAccount", rs.getString("instagram_account"));

                int transcriptId = rs.getInt("transcript_id");
                boolean hasTranscript = !rs.wasNull();
                reel.addProperty("transcriptId", hasTranscript ? transcriptId : null);

                Boolean consistent = rs.getObject("audio_transcript_consistent", Boolean.class);
                String verifiedBy = rs.getString("verified_by_name");
                String transcriptStatus = !hasTranscript ? "no transcript" :
                        (verifiedBy != null ? (Boolean.TRUE.equals(consistent) ? "verified" : "verified (flagged)") : "unverified");
                reel.addProperty("transcriptStatus", transcriptStatus);

                boolean hasGroundTruth = rs.getInt("gt_count") > 0;
                reel.addProperty("hasGroundTruth", hasGroundTruth);

                String languageTag = "N/A";
                if (hasTranscript) {
                    String filePath = rs.getString("file_path");
                    languageTag = detectTranscriptLanguage(filePath);
                }
                reel.addProperty("languageTag", languageTag);

                reels.add(reel);
            }
        } catch (SQLException e) {
            logger.error("Error building reel dashboard: {}", e.getMessage(), e);
        }
        return reels;
    }

    /**
     * Detail for one reel/transcript: full transcript text (for
     * preview + code-switch highlighting on the client) and the
     * status of every model x technique combination run against it.
     */
    public JsonObject getReelDetail(int transcriptId) {
        JsonObject detail = new JsonObject();
        detail.addProperty("transcriptId", transcriptId);

        String pathSql = "SELECT file_path FROM transcript WHERE transcript_id = ?";
        String transcriptText = "";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(pathSql)) {
            pstmt.setInt(1, transcriptId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    transcriptText = readFile(rs.getString("file_path"));
                }
            }
        } catch (SQLException e) {
            logger.error("Error reading transcript path: {}", e.getMessage(), e);
        }
        detail.addProperty("transcriptText", transcriptText);

        JsonArray matrix = new JsonArray();
        String sql = "SELECT m.model_name, pt.technique_name, e.status, e.experiment_id "
            + "FROM llm_model m "
            + "CROSS JOIN prompt_technique pt "
            + "LEFT JOIN experiment e ON e.model_id = m.model_id AND e.technique_id = pt.technique_id AND e.transcript_id = ? "
            + "ORDER BY m.model_name, pt.technique_name ";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, transcriptId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    JsonObject cell = new JsonObject();
                    cell.addProperty("modelName", rs.getString("model_name"));
                    cell.addProperty("techniqueName", rs.getString("technique_name"));
                    String status = rs.getString("status");
                    cell.addProperty("status", status == null ? "not_run" : status);
                    int expId = rs.getInt("experiment_id");
                    cell.addProperty("experimentId", rs.wasNull() ? null : expId);
                    matrix.add(cell);
                }
            }
        } catch (SQLException e) {
            logger.error("Error building reel detail matrix: {}", e.getMessage(), e);
        }
        detail.add("matrix", matrix);

        return detail;
    }

    private String readFile(String path) {
        if (path == null) return "";
        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new FileReader(path))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            logger.warn("Could not read transcript file {}: {}", path, e.getMessage());
            return "";
        }
    }

    private static final java.util.Set<String> MALAY_MARKERS = java.util.Set.of(
        "dan", "dengan", "yang", "ke", "di", "sikit", "sudu", "cawan", "garam", "gula",
        "minyak", "goreng", "masak", "bawang", "cili", "santan", "tepung", "telur", "susu"
    );

    private String detectTranscriptLanguage(String filePath) {
        String text = readFile(filePath);
        if (text.isBlank()) return "N/A";
        boolean hasTamilOrChinese = text.matches("(?s).*[\\u0B80-\\u0BFF\\u4E00-\\u9FFF].*");
        String lower = text.toLowerCase();
        boolean hasMalay = MALAY_MARKERS.stream().anyMatch(lower::contains);
        boolean hasEnglish = text.matches("(?s).*[a-zA-Z]{3,}.*");

        if (hasTamilOrChinese) return "Other";
        if (hasMalay && hasEnglish) return "Code-switched";
        if (hasMalay) return "Malay";
        if (hasEnglish) return "English";
        return "Unknown";
    }
}