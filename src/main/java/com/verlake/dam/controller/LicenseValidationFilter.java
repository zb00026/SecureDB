package com.verlake.dam.controller;

import com.verlake.dam.configuration.LicenseManager;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Component
@Order(1)
public class LicenseValidationFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(LicenseValidationFilter.class);
    
    private final LicenseManager licenseManager;

    public LicenseValidationFilter(LicenseManager licenseManager) {
        this.licenseManager = licenseManager;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Skip license validation for public endpoints
        if (isPublicEndpoint(httpRequest.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        if (!licenseManager.isLicenseValid()) {
            logger.error("License validation failed");
            httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
            httpResponse.getWriter().write("Invalid license");
            return;
        }
        
        chain.doFilter(request, response);
    }

    private boolean isPublicEndpoint(String uri) {
        return uri.startsWith("/public/") || 
               uri.startsWith("/api/auth/verifyToken");
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Initialization logic if needed
    }

    @Override
    public void destroy() {
        // Cleanup logic if needed
    }
} 