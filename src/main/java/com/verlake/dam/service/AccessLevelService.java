package com.verlake.dam.service;

import com.verlake.dam.entity.assets.AccessLevel;
import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.assets.AssetCredential;
import com.verlake.dam.entity.assets.AssetObject;
import com.verlake.dam.entity.assets.AccessLevelObject;
import com.verlake.dam.entity.assets.AccessRequest;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.repository.assets.AccessLevelRepository;
import com.verlake.dam.repository.assets.AssetCredentialsRepository;
import com.verlake.dam.repository.assets.AssetObjectRepository;
import com.verlake.dam.repository.assets.AccessLevelObjectRepository;
import com.verlake.dam.utils.Constants;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;



@Service
@Transactional(readOnly = true)
@Slf4j
public class AccessLevelService {
    
    private final AccessLevelRepository accessLevelRepository;
    private final AssetObjectRepository assetObjectRepository;
    private final AccessLevelObjectRepository accessLevelObjectRepository;

    public AccessLevelService(AccessLevelRepository accessLevelRepository, 
                            AssetObjectRepository assetObjectRepository,
                            AccessLevelObjectRepository accessLevelObjectRepository) {
        this.accessLevelRepository = accessLevelRepository;
        this.assetObjectRepository = assetObjectRepository;
        this.accessLevelObjectRepository = accessLevelObjectRepository;
    }

    public List<String> getAvailableObjects(String assetType, String databaseType) {
        return accessLevelRepository.findDistinctObjectsByAssetTypeAndDatabaseType(assetType, databaseType);
    }

    public List<AccessLevel> getAccessLevels(String assetType, String databaseType) {
        return accessLevelRepository.findByAssetTypeAndDatabaseType(assetType, databaseType);
    }

    public List<String> getTemplateAccesses(String assetType, String databaseType) {
        return accessLevelRepository.findTemplatesByAssetTypeAndDatabaseType(assetType, databaseType);
    }

    public List<AccessLevel> getObjectPermissions(String assetType, String databaseType, String object) {
        return accessLevelRepository.findByAssetTypeAndDatabaseTypeAndObject(assetType, databaseType, object);
    }

    public String getFetchAccessTemplate(String assetType, String databaseType) {
        AccessLevel fetchAccess = accessLevelRepository.findFetchAccessTemplate(assetType, databaseType);
        return fetchAccess != null ? fetchAccess.getAccessTemplate() : null;
    }

    public AccessLevel findById(Long id) {
        return accessLevelRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Access Level not found with id: " + id));
    }

    public List<AccessLevel> findAll() {
        return accessLevelRepository.findAll();
    }

    public ArrayNode getAssetObjectsWithData(Asset asset) {
        List<AccessLevel> accessLevels = getAccessLevels(asset.getType().name(), asset.getDatabaseType().name());
        
        ObjectMapper objectMapper = new ObjectMapper();
        ArrayNode rootArray = objectMapper.createArrayNode();
        
        // Group access levels by object type
        Map<String, List<AccessLevel>> groupedLevels = accessLevels.stream()
            .collect(Collectors.groupingBy(
                AccessLevel::getObject, 
                TreeMap::new,  // This will sort keys alphabetically
                Collectors.toList()
            ));
        
        // Create array elements for each object type
        groupedLevels.forEach((objectType, levels) -> {
            ObjectNode categoryNode = objectMapper.createObjectNode();
            categoryNode.put("name", objectType.toUpperCase());
            
            // Add grants array
            ArrayNode grantsNode = categoryNode.putArray(Constants.ACCESS_OBJECT_ATTR_GRANTS);
            levels.forEach(level -> {
                ObjectNode levelNode = objectMapper.createObjectNode();
                levelNode.put("id", level.getId());
                levelNode.put(Constants.ACCESS_LEVEL_ATTR_TEMPLATE, level.getTemplates());
                levelNode.put("accessTemplate", level.getAccessTemplate());
                levelNode.put("object", level.getObject());
                grantsNode.add(levelNode);
            });
            
            // Add data array from AssetObject's objectsJson
            ArrayNode dataNode = categoryNode.putArray(Constants.ACCESS_OBJECT_ATTR_DATA);
            List<AssetObject> assetObjects = assetObjectRepository.findByAsset(asset);
            
            assetObjects.forEach(assetObject -> {
                try {
                    ObjectNode objectsJson = (ObjectNode) objectMapper.readTree(assetObject.getObjectsJson());
                    if (objectsJson.has(objectType.toUpperCase())) {
                        ObjectNode categoryData = (ObjectNode) objectsJson.get(objectType.toUpperCase());
                        if (categoryData.has(Constants.ACCESS_OBJECT_ATTR_DATA)) {
                            categoryData.get(Constants.ACCESS_OBJECT_ATTR_DATA).forEach(dataNode::add);
                        }
                    }
                } catch (Exception e) {
                    log.error("Error parsing objectsJson for asset object: " + assetObject.getId(), e);
                }
            });
            
            rootArray.add(categoryNode);
        });
        
        return rootArray;
    }

    public List<AccessLevelObject> getAccessLevelObjects(AccessRequest request) {
        return accessLevelObjectRepository.findByAccessRequestId(request.getId());
    }
} 