package edu.utem.ftmk.masakgramprompt.dao;

import edu.utem.ftmk.masakgramprompt.db.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * ReferenceDataDAO handles operations for reference data:
 * transcripts, LLM models, and prompt techniques
 */
public class ReferenceDataDAO {
    private static final Logger logger = LoggerFactory.getLogger(ReferenceDataDAO.class);
    private DatabaseConnection dbConnection;
    
    public ReferenceDataDAO() {
        this.dbConnection = DatabaseConnection.getInstance();
    }
    
    /**
     * Get all available LLM models
     * @return List of model details
     */
    public List<Map<String, Object>> getAllModels() {
        String sql = "SELECT model_id, model_name, model_tag, provider, description FROM llm_model";
        List<Map<String, Object>> models = new ArrayList<>();
        
        try (PreparedStatement pstmt = dbConnection.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> model = new HashMap<>();
                model.put("modelId", rs.getInt("model_id"));
                model.put("modelName", rs.getString("model_name"));
                model.put("modelTag", rs.getString("model_tag"));
                model.put("provider", rs.getString("provider"));
                model.put("description", rs.getString("description"));
                models.add(model);
            }
        } catch (SQLException e) {
            logger.error("Error fetching models: {}", e.getMessage());
        }
        
        return models;
    }
    
    /**
     * Get model by ID
     * @param modelId Model ID
     * @return Model details or null if not found
     */
    public Map<String, Object> getModelById(int modelId) {
        String sql = "SELECT * FROM llm_model WHERE model_id = ?";
        
        try (PreparedStatement pstmt = dbConnection.prepareStatement(sql)) {
            pstmt.setInt(1, modelId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> model = new HashMap<>();
                    model.put("modelId", rs.getInt("model_id"));
                    model.put("modelName", rs.getString("model_name"));
                    model.put("modelTag", rs.getString("model_tag"));
                    model.put("provider", rs.getString("provider"));
                    return model;
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching model: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Get model by name
     * @param modelName Model name
     * @return Model details with ID or null
     */
    public Map<String, Object> getModelByName(String modelName) {
        String sql = "SELECT model_id, model_tag FROM llm_model WHERE model_name LIKE ?";
        
        try (PreparedStatement pstmt = dbConnection.prepareStatement(sql)) {
            pstmt.setString(1, "%" + modelName + "%");
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> model = new HashMap<>();
                    model.put("modelId", rs.getInt("model_id"));
                    model.put("modelTag", rs.getString("model_tag"));
                    return model;
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching model by name: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Get all available prompt techniques
     * @return List of technique details
     */
    public List<Map<String, Object>> getAllTechniques() {
        String sql = "SELECT technique_id, technique_name, system_prompt_file, user_prompt_file, " +
                     "prompt_version, description FROM prompt_technique";
        List<Map<String, Object>> techniques = new ArrayList<>();
        
        try (PreparedStatement pstmt = dbConnection.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> tech = new HashMap<>();
                tech.put("techniqueId", rs.getInt("technique_id"));
                tech.put("techniqueName", rs.getString("technique_name"));
                tech.put("systemPromptFile", rs.getString("system_prompt_file"));
                tech.put("userPromptFile", rs.getString("user_prompt_file"));
                tech.put("promptVersion", rs.getString("prompt_version"));
                tech.put("description", rs.getString("description"));
                techniques.add(tech);
            }
        } catch (SQLException e) {
            logger.error("Error fetching techniques: {}", e.getMessage());
        }
        
        return techniques;
    }
    
    /**
     * Get technique by ID
     * @param techniqueId Technique ID
     * @return Technique details or null
     */
    public Map<String, Object> getTechniqueById(int techniqueId) {
        String sql = "SELECT * FROM prompt_technique WHERE technique_id = ?";
        
        try (PreparedStatement pstmt = dbConnection.prepareStatement(sql)) {
            pstmt.setInt(1, techniqueId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> tech = new HashMap<>();
                    tech.put("techniqueId", rs.getInt("technique_id"));
                    tech.put("techniqueName", rs.getString("technique_name"));
                    tech.put("systemPromptFile", rs.getString("system_prompt_file"));
                    tech.put("userPromptFile", rs.getString("user_prompt_file"));
                    return tech;
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching technique: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Get technique by name
     * @param techniqueName Technique name
     * @return Technique details with ID or null
     */
    public Map<String, Object> getTechniqueByName(String techniqueName) {
        String sql = "SELECT * FROM prompt_technique WHERE technique_name = ?";
        
        try (PreparedStatement pstmt = dbConnection.prepareStatement(sql)) {
            pstmt.setString(1, techniqueName);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> tech = new HashMap<>();
                    tech.put("techniqueId", rs.getInt("technique_id"));
                    tech.put("systemPromptFile", rs.getString("system_prompt_file"));
                    tech.put("userPromptFile", rs.getString("user_prompt_file"));
                    return tech;
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching technique by name: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Get all transcripts
     * @return List of transcript details
     */
    public List<Map<String, Object>> getAllTranscripts() {
        String sql = "SELECT t.transcript_id, t.reel_id, r.reel_url, r.identified_by_name, " +
                     "t.file_path, t.file_name, t.audio_transcript_consistent " +
                     "FROM transcript t " +
                     "JOIN reel r ON t.reel_id = r.reel_id " +
                     "ORDER BY t.transcript_id";
        
        List<Map<String, Object>> transcripts = new ArrayList<>();
        
        try (PreparedStatement pstmt = dbConnection.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> transcript = new HashMap<>();
                transcript.put("transcriptId", rs.getInt("transcript_id"));
                transcript.put("reelId", rs.getInt("reel_id"));
                transcript.put("reelUrl", rs.getString("reel_url"));
                transcript.put("identifiedBy", rs.getString("identified_by_name"));
                transcript.put("filePath", rs.getString("file_path"));
                transcript.put("fileName", rs.getString("file_name"));
                transcript.put("verified", rs.getBoolean("audio_transcript_consistent"));
                transcripts.add(transcript);
            }
        } catch (SQLException e) {
            logger.error("Error fetching transcripts: {}", e.getMessage());
        }
        
        return transcripts;
    }
    
    /**
     * Get transcript by ID with full content
     * @param transcriptId Transcript ID
     * @return Transcript details with file content
     */
    public Map<String, Object> getTranscriptById(int transcriptId) {
        String sql = "SELECT t.*, r.reel_url, r.identified_by_name " +
                     "FROM transcript t " +
                     "JOIN reel r ON t.reel_id = r.reel_id " +
                     "WHERE t.transcript_id = ?";
        
        try (PreparedStatement pstmt = dbConnection.prepareStatement(sql)) {
            pstmt.setInt(1, transcriptId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> transcript = new HashMap<>();
                    transcript.put("transcriptId", rs.getInt("transcript_id"));
                    transcript.put("reelId", rs.getInt("reel_id"));
                    transcript.put("filePath", rs.getString("file_path"));
                    transcript.put("reelUrl", rs.getString("reel_url"));
                    return transcript;
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching transcript: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Get transcript content from file
     * @param filePath Path to transcript file
     * @return File content as string
     */
    public String getTranscriptContent(String filePath) {
        try {
            return new String(java.nio.file.Files.readAllBytes(
                    java.nio.file.Paths.get(filePath)));
        } catch (Exception e) {
            logger.error("Error reading transcript file: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Get dashboard summary data
     * @return Dashboard metrics
     */
    public Map<String, Object> getDashboardSummary() {
        Map<String, Object> summary = new HashMap<>();
        
        try {
            // Total reels
            try (Statement stmt = dbConnection.getConnection().createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) as count FROM reel")) {
                if (rs.next()) {
                    summary.put("totalReels", rs.getInt("count"));
                }
            }
            
            // Total experiments
            try (Statement stmt = dbConnection.getConnection().createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) as count FROM experiment")) {
                if (rs.next()) {
                    summary.put("totalExperiments", rs.getInt("count"));
                }
            }
            
            // Experiments by status
            try (Statement stmt = dbConnection.getConnection().createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT status, COUNT(*) as count FROM experiment GROUP BY status")) {
                Map<String, Integer> statusCount = new HashMap<>();
                while (rs.next()) {
                    statusCount.put(rs.getString("status"), rs.getInt("count"));
                }
                summary.put("experimentsByStatus", statusCount);
            }
            
        } catch (SQLException e) {
            logger.error("Error fetching dashboard summary: {}", e.getMessage());
        }
        
        return summary;
    }
}