package com.suraj.camel.kafka.shopcart.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.MongoClient;


@Configuration
public class MongoConfig {
	
	@Value("${spring.data.mongodb.host}")
    private String host;
	
	@Value("${spring.data.mongodb.port}")
    private Integer port;
	
	@Value("${spring.data.mongodb.database}")
	private String database;

    @Bean("mongoClient")
    public MongoClient getMongoClient() {
        return new MongoClient(host, port);
    }
    
    @Bean
    public MongoTemplate mongoTemplate(@Qualifier("mongoClient") MongoClient mongoClient) {
    	return new MongoTemplate(mongoClient, database);
    }

}
