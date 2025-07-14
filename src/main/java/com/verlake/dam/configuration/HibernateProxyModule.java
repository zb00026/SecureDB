package com.verlake.dam.configuration;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Custom Jackson module to handle Hibernate proxy serialization
 */
@Component
public class HibernateProxyModule extends SimpleModule {

    public HibernateProxyModule() {
        super("HibernateProxyModule");
        addSerializer(HibernateProxy.class, new HibernateProxySerializer());
        addSerializer(org.hibernate.proxy.pojo.bytebuddy.ByteBuddyInterceptor.class, new ByteBuddyInterceptorSerializer());
    }

    /**
     * Custom serializer for Hibernate proxies
     */
    private static class HibernateProxySerializer extends JsonSerializer<HibernateProxy> {
        @Override
        public void serialize(HibernateProxy value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (value == null) {
                gen.writeNull();
            } else {
                // Try to get the actual entity
                Object entity = value.getHibernateLazyInitializer().getImplementation();
                if (entity != null) {
                    // Serialize the actual entity
                    serializers.defaultSerializeValue(entity, gen);
                } else {
                    // If we can't get the entity, just serialize the ID if available
                    Object id = value.getHibernateLazyInitializer().getIdentifier();
                    if (id != null) {
                        gen.writeObject(id);
                    } else {
                        gen.writeNull();
                    }
                }
            }
        }
    }
    
    /**
     * Custom serializer for ByteBuddyInterceptor
     */
    private static class ByteBuddyInterceptorSerializer extends JsonSerializer<org.hibernate.proxy.pojo.bytebuddy.ByteBuddyInterceptor> {
        @Override
        public void serialize(org.hibernate.proxy.pojo.bytebuddy.ByteBuddyInterceptor value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            // Simply write null to avoid serialization issues
            gen.writeNull();
        }
    }
} 