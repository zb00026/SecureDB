package com.verlake.dam.entity.dto;

import lombok.Data;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@Data
public class PageRequestDTO {
    private Integer page;  // Changed to Integer to allow null
    private Integer perPage;
    
    public Pageable toPageRequest() {
        if (page == null || perPage == null) {
            return Pageable.unpaged();  // Return unpaged when parameters are not specified
        }
        // Adjust page number to be 0-based
        int validPage = Math.max(0, page - 1);  // Convert 1-based to 0-based
        int validSize = Math.max(1, perPage);
        return PageRequest.of(validPage, validSize);
    }
    
    public Pageable toPageRequest(Sort sort) {
        if (page == null || perPage == null) {
            return Pageable.unpaged();  // Return unpaged with sort
        }
        int validPage = Math.max(0, page - 1);  // Convert 1-based to 0-based
        int validSize = Math.max(1, perPage);
        return PageRequest.of(validPage, validSize, sort);
    }
} 