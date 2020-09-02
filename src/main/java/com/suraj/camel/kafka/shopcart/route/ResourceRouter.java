package com.suraj.camel.kafka.shopcart.route;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.model.rest.RestBindingMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import com.suraj.camel.kafka.shopcart.document.Product;

@Component
public class ResourceRouter extends RouteBuilder {

	private static final String SUCCESS_MSG = "Data Inserted Successfully";
	private static final String FAIL_MSG = "Data Insertion Failed";
	private static final String ROLLBACKROUTE = "direct:rollback";

	@Value("${retry.delay.pattern}")
	private String redeliveryDelayPattern;

	@Value("${retry.count}")
	private int retryCount;
	
	@Value("${collection}")
	private String collection;
	
	@Autowired
	private MongoTemplate mongoTemplate;

	@Override
	public void configure() throws Exception {
		
		onException(Exception.class)
		.log(LoggingLevel.ERROR, "Exception Occurred  ${exception}")
		.handled(true)
		.maximumRedeliveries(retryCount)
		.delayPattern(redeliveryDelayPattern)
		.end()
		.to(ROLLBACKROUTE);

		restConfiguration()
		.component("netty-http")
		.host("localhost")
		.port(8085)
		.bindingMode(RestBindingMode.json);

		rest("/shopping")
			.post("addNewProduct")
			.consumes("application/json")
			.produces("application/json")
			.to("direct:push-to-kafka");

		from("direct:push-to-kafka")
			.routeId("push-to-kafka")
			.log(LoggingLevel.INFO, "Pushing data to Kafka")
			.marshal()
			.json(JsonLibrary.Jackson)
			.to("kafka:ProductDetails?brokers={{kafka.broker}}")
			.log(LoggingLevel.INFO, " Successfully pushed to kafka topic ")
			.setBody(simple(SUCCESS_MSG))
			.end();

		from("kafka:ProductDetails?brokers={{kafka.broker}}&autoOffsetReset=earliest&groupId=product&maxPollRecords=3&consumersCount=1&seekTo=")
			.routeId("consumerRoute")
			.log(LoggingLevel.INFO, " Retrieving data from topic : ${header[kafka.TOPIC]} , Body  :  ${in.body} ")
			.to("mongodb:mongoClient?database={{spring.data.mongodb.database}}&collection={{collection}}&operation=insert")
			.log(LoggingLevel.INFO, "Successfully inserted in Database");

		// Rollback Route
		from(ROLLBACKROUTE)
			.routeId("rollback-Route")
			.process(exchange -> {
				this.rollbackChanges(exchange);
			})
			.log(LoggingLevel.INFO, "Failure message: ${in.body} ")
			.setBody(simple(FAIL_MSG))
			.end();

	}

	private void rollbackChanges(Exchange exchange) {
		Product product = exchange.getIn().getBody(Product.class);
		Query query = new Query();
		query.addCriteria(Criteria.where("productId").is(product.getProductId()));
		mongoTemplate.remove(query, collection);
		
	}

}
