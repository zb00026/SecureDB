package com.verlake.dam.entity.assets;

import com.verlake.dam.entity.user.User;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "asset_query_change_requests")
@Data
public class AssetQueryChangeRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "asset_id")
    private Asset asset;
    
    @ManyToOne
    @JoinColumn(name = "requestor_id")
    private User requestor;
    
    @Column(name = "ticket_reference")
    private String ticketReference;
    
    @Column(name = "change_description", columnDefinition = "text")
    private String changeDescription;
    
    @Column(name = "query", columnDefinition = "text")
    private String query;
}
