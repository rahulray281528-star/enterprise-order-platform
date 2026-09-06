package com.enterprise.common.config;

import com.enterprise.common.constant.HeaderNames;
import com.enterprise.common.util.RequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Accepts an inbound correlation id or mints one, puts it in the MDC so every log line
 * carries it, and echoes it back on the response. This is what makes a request traceable
 * across gateway to order to payment to notification.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = request.getHeader(HeaderNames.CORRELATION_ID);
        if (!StringUtils.hasText(correlationId)) {
            correlationId = RequestContext.newCorrelationId();
        }
        RequestContext.setCorrelationId(correlationId);
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HeaderNames.CORRELATION_ID, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
            RequestContext.clear();
        }
    }
}
