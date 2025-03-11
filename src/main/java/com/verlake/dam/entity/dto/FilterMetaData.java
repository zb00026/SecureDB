package com.verlake.dam.entity.dto;

import org.springframework.data.jpa.domain.Specification;

public interface FilterMetaData<T> {
    Specification<T> toSpecification();
}