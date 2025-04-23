package com.verlake.dam.repository.assets;

import com.verlake.dam.entity.assets.AssetCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AssetCredentialRepository extends JpaRepository<AssetCredential, Long> {
} 