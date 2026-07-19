package edu.utem.ftmk.masakgramprompt.db;

import java.sql.*;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DatabaseConnection provides a singleton connection manager for MySQL database.
 * Handles connection lifecycle and provides static access to database operations.
 * 
 * Configuration: Update the connection details below to match your MySQL setup.
 */
public class DatabaseConnection {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseConnection.class);
    
    // Database configuration
    private static final String DB_URL = "jdbc:mysql://localhost:3307/masakgramprompt";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = ""; 
    private static final String DB_DRIVER = "com.mysql.cj.jdbc.Driver";
    
    private static DatabaseConnection instance;
    private Connection connection;
    
    /**
     * Private constructor to enforce singleton pattern
     */
    private DatabaseConnection() {
        this.connection = null;
    }
    
    /**
     * Get singleton instance of DatabaseConnection
     * @return DatabaseConnection instance
     */
    public static synchronized DatabaseConnection getInstance() {
        if (instance == null) {
            instance = new DatabaseConnection();
        }
        return instance;
    }
    
    /**
     * Establish connection to MySQL database
     * @return true if connection successful, false otherwise
     */
    public boolean connect() {
        try {
            // Load MySQL JDBC driver
            Class.forName(DB_DRIVER);
            
            // Create connection
            this.connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            
            // Test connection
            if (testConnection()) {
                logger.info("Database connection established successfully");
                return true;
            } else {
                logger.error("Database connection test failed");
                return false;
            }
        } catch (ClassNotFoundException e) {
            logger.error("MySQL JDBC Driver not found: {}", e.getMessage());
            return false;
        } catch (SQLException e) {
            logger.error("Failed to connect to database: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Test if the connection is alive
     * @return true if connection is valid, false otherwise
     */
    public boolean testConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                return false;
            }
            // Try a simple query to verify connection
            try (Statement stmt = connection.createStatement()) {
                stmt.executeQuery("SELECT 1");
                return true;
            }
        } catch (SQLException e) {
            logger.warn("Connection test failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Get current database connection
     * @return Connection object
     */
    public Connection getConnection() {
        if (connection == null || testConnection() == false) {
            logger.warn("Connection is closed or null, attempting to reconnect...");
            connect();
        }
        return connection;
    }
    
    /**
     * Prepare a SQL statement
     * @param sql SQL query string
     * @return PreparedStatement object
     * @throws SQLException if statement preparation fails
     */
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        if (connection == null || connection.isClosed()) {
            connect();
        }
        return connection.prepareStatement(sql);
    }
    
    /**
     * Execute a SQL query and return ResultSet
     * @param sql SQL query string
     * @return ResultSet from the query
     * @throws SQLException if query execution fails
     */
    public ResultSet executeQuery(String sql) throws SQLException {
        if (connection == null || connection.isClosed()) {
            connect();
        }
        try (Statement stmt = connection.createStatement()) {
            return stmt.executeQuery(sql);
        }
    }
    
    /**
     * Execute a SQL update, insert, or delete
     * @param sql SQL statement
     * @return number of rows affected
     * @throws SQLException if execution fails
     */
    public int executeUpdate(String sql) throws SQLException {
        if (connection == null || connection.isClosed()) {
            connect();
        }
        try (Statement stmt = connection.createStatement()) {
            return stmt.executeUpdate(sql);
        }
    }
    
    /**
     * Close the database connection
     */
    public void disconnect() {
        if (connection != null) {
            try {
                connection.close();
                logger.info("Database connection closed");
            } catch (SQLException e) {
                logger.error("Error closing database connection: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Close a ResultSet
     * @param rs ResultSet to close
     */
    public static void closeResultSet(ResultSet rs) {
        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException e) {
                logger.warn("Error closing ResultSet: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Close a PreparedStatement
     * @param pstmt PreparedStatement to close
     */
    public static void closePreparedStatement(PreparedStatement pstmt) {
        if (pstmt != null) {
            try {
                pstmt.close();
            } catch (SQLException e) {
                logger.warn("Error closing PreparedStatement: {}", e.getMessage());
            }
        }
    }
}