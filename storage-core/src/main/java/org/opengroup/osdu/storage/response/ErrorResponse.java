package org.opengroup.osdu.storage.response;

public class ErrorResponse {
    private final int code;
    private final String reason;
    private final String message;

    public ErrorResponse(int code, String reason, String message) {
        this.code = code;
        this.reason = reason;
        this.message = message;
    }
    
    public int getCode() {
        return code;
    }

    public String getReason() {
        return reason;
    }

    public String getMessage() {
        return message;
    }

    public String toString(){
        return "{\"code\": " + code + ",\"reason\": \"" + reason + "\",\"message\": \"" + message + "\"}";
    }
}
