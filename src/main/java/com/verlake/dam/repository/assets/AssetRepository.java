package com.verlake.dam.repository.assets;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.verlake.dam.entity.assets.Asset;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssetRepository extends JpaRepository<Asset, Long> {
    List<Asset> findByDeletedFalse();
    Optional<Asset> findByIdAndDeletedFalse(Long id);
    boolean existsByName(String name);
} 