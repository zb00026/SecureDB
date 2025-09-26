package com.verlake.dam.entity.dto.unix;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FolderSuggestionsRequestDTO {
    
    @NotBlank(message = "Path is required")
    private String path;
    
    public FolderSuggestionsRequestDTO() {
        this.path = "/";
    }
    
    public FolderSuggestionsRequestDTO(String path) {
        this.path = path != null && !path.isEmpty() ? path : "/";
    }
}
