package edu.utem.ftmk.masakgramprompt.network;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * NetworkProtocol defines the message format for TCP/IP communication
 * between client and server. All messages are JSON-based for flexibility.
 *
 * FIXED: Added "messageType" discriminator field to StatusUpdate and
 * ResponseMessage so the client can tell them apart on the same socket
 * stream (they were previously indistinguishable by field name alone,
 * causing the client to misread the first StatusUpdate as the final
 * ResponseMessage).
 */
public class NetworkProtocol {
    private static final Gson gson = new Gson();

    public static class RequestMessage implements Serializable {
        public String requestId;
        public String command;
        public String modelName;
        public String technique;
        public int transcriptId;
        public String transcript;
        public Map<String, Object> parameters;

        public RequestMessage() {
            this.parameters = new HashMap<>();
        }

        public String toJson() {
            return gson.toJson(this);
        }

        public static RequestMessage fromJson(String json) {
            return gson.fromJson(json, RequestMessage.class);
        }

        @Override
        public String toString() {
            return "RequestMessage{" +
                    "requestId='" + requestId + '\'' +
                    ", command='" + command + '\'' +
                    ", modelName='" + modelName + '\'' +
                    ", technique='" + technique + '\'' +
                    ", transcriptId=" + transcriptId +
                    '}';
        }
    }

    /**
     * Response message from server to client
     */
    public static class ResponseMessage implements Serializable {
        public String messageType = "RESPONSE";  // discriminator
        public String requestId;
        public int statusCode;
        public String statusMessage;
        public boolean success;
        public Object data;
        public String error;

        public ResponseMessage() {
            this.statusCode = 200;
            this.statusMessage = "OK";
            this.success = true;
        }

        public String toJson() {
            return gson.toJson(this);
        }

        public static ResponseMessage fromJson(String json) {
            return gson.fromJson(json, ResponseMessage.class);
        }

        @Override
        public String toString() {
            return "ResponseMessage{" +
                    "requestId='" + requestId + '\'' +
                    ", statusCode=" + statusCode +
                    ", statusMessage='" + statusMessage + '\'' +
                    ", success=" + success +
                    '}';
        }
    }

    /**
     * Status update message sent from server to client during execution
     */
    public static class StatusUpdate implements Serializable {
        public String messageType = "STATUS";  // discriminator
        public String experimentId;
        public String modelName;
        public String technique;
        public String status;
        public int progress;
        public String message;
        public long timestamp;

        public StatusUpdate() {
            this.timestamp = System.currentTimeMillis();
        }

        public String toJson() {
            return gson.toJson(this);
        }

        public static StatusUpdate fromJson(String json) {
            return gson.fromJson(json, StatusUpdate.class);
        }
    }

    public static class ExperimentResult implements Serializable {
        public int experimentId;
        public int transcriptId;
        public String modelName;
        public String technique;
        public String recipeName;
        public int servingsEstimated;
        public JsonObject amountPerServing;
        public JsonObject nutritionTotal;
        public JsonElement ingredients;
        public String rawJsonOutput;
        public boolean jsonValid;

        public String toJson() {
            return gson.toJson(this);
        }

        public static ExperimentResult fromJson(String json) {
            return gson.fromJson(json, ExperimentResult.class);
        }
    }

    public static class Commands {
        public static final String RUN_EXPERIMENT = "RUN_EXPERIMENT";
        public static final String GET_STATUS = "GET_STATUS";
        public static final String CANCEL_EXPERIMENT = "CANCEL_EXPERIMENT";
        public static final String GET_EXPERIMENTS = "GET_EXPERIMENTS";
        public static final String EXPORT_DATA = "EXPORT_DATA";
        public static final String GET_DASHBOARD = "GET_DASHBOARD";
        public static final String GET_MODELS = "GET_MODELS";
        public static final String GET_TECHNIQUES = "GET_TECHNIQUES";
        public static final String GET_TRANSCRIPTS = "GET_TRANSCRIPTS";
        public static final String PING = "PING";
    }

    public static class StatusCodes {
        public static final int OK = 200;
        public static final int CREATED = 201;
        public static final int ACCEPTED = 202;
        public static final int BAD_REQUEST = 400;
        public static final int UNAUTHORIZED = 401;
        public static final int FORBIDDEN = 403;
        public static final int NOT_FOUND = 404;
        public static final int CONFLICT = 409;
        public static final int INTERNAL_ERROR = 500;
        public static final int SERVICE_UNAVAILABLE = 503;
    }
}