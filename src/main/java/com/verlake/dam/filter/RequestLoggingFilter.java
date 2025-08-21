package com.verlake.dam.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class RequestLoggingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        // Wrap request and response to cache content
        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(httpRequest);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(httpResponse);
        
        // Log request details
        logRequest(requestWrapper);
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Continue with the filter chain
            chain.doFilter(requestWrapper, responseWrapper);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            
            // Log response details
            logResponse(responseWrapper, duration);
            
            // Copy cached response content back to original response
            responseWrapper.copyBodyToResponse();
        }
    }
    
    private void logRequest(ContentCachingRequestWrapper request) {
        log.info("=== INCOMING REQUEST ===");
        log.info("Method: {}", request.getMethod());
        log.info("URL: {}", request.getRequestURL().toString());
        log.info("URI: {}", request.getRequestURI());
        log.info("Query String: {}", request.getQueryString());
        log.info("Remote Address: {}", request.getRemoteAddr());
        log.info("Remote Host: {}", request.getRemoteHost());
        log.info("Remote Port: {}", request.getRemotePort());
        log.info("Local Address: {}", request.getLocalAddr());
        log.info("Local Port: {}", request.getLocalPort());
        log.info("Protocol: {}", request.getProtocol());
        log.info("Scheme: {}", request.getScheme());
        log.info("Server Name: {}", request.getServerName());
        log.info("Server Port: {}", request.getServerPort());
        log.info("Content Type: {}", request.getContentType());
        log.info("Content Length: {}", request.getContentLength());
        log.info("Character Encoding: {}", request.getCharacterEncoding());
        
        // Log all headers
        log.info("=== REQUEST HEADERS ===");
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = request.getHeader(headerName);
            headers.put(headerName, headerValue);
            log.info("{}: {}", headerName, headerValue);
        }
        
        // Log request parameters
        log.info("=== REQUEST PARAMETERS ===");
        Map<String, String[]> parameterMap = request.getParameterMap();
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            log.info("{}: {}", entry.getKey(), String.join(", ", entry.getValue()));
        }
        
        // Log request body (for POST/PUT requests)
        if ("POST".equalsIgnoreCase(request.getMethod()) || 
            "PUT".equalsIgnoreCase(request.getMethod()) || 
            "PATCH".equalsIgnoreCase(request.getMethod())) {
            
            byte[] content = request.getContentAsByteArray();
            if (content.length > 0) {
                try {
                    String body = new String(content, request.getCharacterEncoding() != null ? 
                        request.getCharacterEncoding() : "UTF-8");
                    log.info("=== REQUEST BODY ===");
                    log.info("Body Length: {} bytes", content.length);
                    
                    // Truncate very long bodies for logging
                    if (body.length() > 2000) {
                        log.info("Body (truncated): {}...", body.substring(0, 2000));
                        log.info("Body was truncated. Full length: {} characters", body.length());
                    } else {
                        log.info("Body: {}", body);
                    }
                } catch (Exception e) {
                    log.warn("Failed to decode request body: {}", e.getMessage());
                    log.info("=== REQUEST BODY ===");
                    log.info("Body Length: {} bytes (could not decode as text)", content.length);
                }
            } else {
                log.info("=== REQUEST BODY ===");
                log.info("No request body");
            }
        }
        
        // Log session information
        if (request.getSession(false) != null) {
            log.info("=== SESSION INFO ===");
            log.info("Session ID: {}", request.getSession().getId());
            log.info("Session Creation Time: {}", request.getSession().getCreationTime());
            log.info("Session Last Accessed: {}", request.getSession().getLastAccessedTime());
            log.info("Session Max Inactive Interval: {}", request.getSession().getMaxInactiveInterval());
        }
        
        log.info("=== END REQUEST DETAILS ===");
    }
    
    private void logResponse(ContentCachingResponseWrapper response, long duration) {
        log.info("=== OUTGOING RESPONSE ===");
        log.info("Status Code: {}", response.getStatus());
        log.info("Content Type: {}", response.getContentType());
        log.info("Character Encoding: {}", response.getCharacterEncoding());
        log.info("Content Length: {}", response.getContentSize());
        log.info("Processing Time: {} ms", duration);
        
        // Log response headers
        log.info("=== RESPONSE HEADERS ===");
        for (String headerName : response.getHeaderNames()) {
            log.info("{}: {}", headerName, String.join(", ", response.getHeaders(headerName)));
        }
        
        // Log response body (truncated for large responses)
        byte[] content = response.getContentAsByteArray();
        if (content.length > 0) {
            try {
                String body = new String(content, response.getCharacterEncoding() != null ? 
                    response.getCharacterEncoding() : "UTF-8");
                log.info("=== RESPONSE BODY ===");
                log.info("Body Length: {} bytes", content.length);
                
                // Truncate very long responses for logging
                if (body.length() > 1000) {
                    log.info("Body (truncated): {}...", body.substring(0, 1000));
                    log.info("Body was truncated. Full length: {} characters", body.length());
                } else {
                    log.info("Body: {}", body);
                }
            } catch (Exception e) {
                log.warn("Failed to decode response body: {}", e.getMessage());
                log.info("=== RESPONSE BODY ===");
                log.info("Body Length: {} bytes (could not decode as text)", content.length);
            }
        } else {
            log.info("=== RESPONSE BODY ===");
            log.info("No response body");
        }
        
        log.info("=== END RESPONSE DETAILS ===");
        log.info(""); // Empty line for readability between requests
    }
}