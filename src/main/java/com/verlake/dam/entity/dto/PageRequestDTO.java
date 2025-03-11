package com.verlake.dam.entity.dto;

import lombok.Data;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Data
public class PageRequestDTO {
    private int page = 0;  // Default to first page (0)
    private int perPage = 20;
    
    public PageRequest toPageRequest() {
        // Adjust page number to be 0-based
        int validPage = Math.max(0, page - 1);  // Convert 1-based to 0-based
        int validSize = Math.max(1, perPage);
        return PageRequest.of(validPage, validSize);
    }
    
    public PageRequest toPageRequest(Sort sort) {
        int validPage = Math.max(0, page - 1);  // Convert 1-based to 0-based
        int validSize = Math.max(1, perPage);
        return PageRequest.of(validPage, validSize, sort);
    }
} 