package com.enterprise.order.config;

import com.enterprise.common.constant.HeaderNames;
import com.enterprise.common.exception.BusinessException;
import com.enterprise.common.exception.InsufficientInventoryException;
import com.enterprise.common.exception.ResourceNotFoundException;
import com.enterprise.common.exception.ServiceUnavailableException;
import com.enterprise.common.util.RequestContext;
import feign.RequestInterceptor;
import feign.Response;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class FeignConfig {

    /**
     * Forwards the caller's bearer token and correlation id on outbound calls, so the
     * downstream service can authorise the request and the whole flow shares one trace id.
     */
    @Bean
    public RequestInterceptor propagateContext() {
        return template -> {
            template.header(HeaderNames.CORRELATION_ID, RequestContext.getCorrelationId());

            var attributes = RequestContextHolder.getRequestAttributes();
            if (attributes instanceof ServletRequestAttributes servletAttributes) {
                String authorization = servletAttributes.getRequest().getHeader("Authorization");
                if (authorization != null && !authorization.isBlank()) {
                    template.header("Authorization", authorization);
                }
            }
        };
    }

    /**
     * Turns a downstream HTTP status into the platform's own exception type, so a 409
     * from inventory surfaces to the customer as INSUFFICIENT_INVENTORY rather than a
     * generic 500.
     */
    @Bean
    public ErrorDecoder errorDecoder() {
        return (methodKey, response) -> decode(methodKey, response);
    }

    private static Exception decode(String methodKey, Response response) {
        int status = response.status();
        return switch (status) {
            case 404 -> new ResourceNotFoundException("Downstream resource not found (" + methodKey + ")");
            case 409 -> new InsufficientInventoryException("Requested quantity is not available");
            case 503 -> new ServiceUnavailableException("Downstream service unavailable (" + methodKey + ")");
            default -> status >= 500
                    ? new ServiceUnavailableException("Downstream error " + status + " from " + methodKey)
                    : new BusinessException("Downstream rejected the request (" + status + ")",
                            "DOWNSTREAM_ERROR", status);
        };
    }
}
