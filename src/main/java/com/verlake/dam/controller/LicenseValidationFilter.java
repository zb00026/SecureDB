package com.verlake.dam.controller;

import com.verlake.dam.configuration.LicenseManager;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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
            // Check if user is admin and accessing admin-related endpoints
            if (isAdminUser() && isAdminAccessibleEndpoint(httpRequest.getRequestURI())) {
                logger.warn("License invalid but allowing admin access to: {}", httpRequest.getRequestURI());
                chain.doFilter(request, response);
                return;
            }
            
            logger.error("License validation failed for non-admin or non-admin endpoint: {}", httpRequest.getRequestURI());
            httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
            httpResponse.getWriter().write("Invalid license");
            return;
        }
        
        chain.doFilter(request, response);
    }

    private boolean isPublicEndpoint(String uri) {
        return uri.startsWith("/public/") || 
               uri.startsWith("/api/auth/verifyToken") ||
               uri.startsWith("/api/license/status");
    }
    
    /**
     * Check if the current user has admin role
     */
    private boolean isAdminUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals("ROLE_ADMIN"));
    }
    
    /**
     * Check if the endpoint should be accessible to admins even when license is invalid
     */
    private boolean isAdminAccessibleEndpoint(String uri) {
        return uri.startsWith("/api/admin/license") ||           // License management endpoints
               uri.startsWith("/api/admin/users") ||             // User management (to manage admin users)
               uri.startsWith("/api/admin/assets") ||            // Asset management endpoints
               uri.startsWith("/api/admin/roles") ||             // Role management endpoints
               uri.startsWith("/api/admin/settings") ||          // Settings management endpoints
               uri.startsWith("/api/license/status");            // License status check
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