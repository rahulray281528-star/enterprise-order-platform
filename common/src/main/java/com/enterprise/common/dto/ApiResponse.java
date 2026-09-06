package com.enterprise.common.dto;

import com.enterprise.common.util.RequestContext;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Standard envelope returned by every successful endpoint on the platform. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private T data;
    private Instant timestamp;
    private String traceId;

    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(Instant.now())
                .traceId(RequestContext.getCorrelationId())
                .build();
    }

    public static <T> ApiResponse<T> success(T data) {
        return success(data, "OK");
    }
}
