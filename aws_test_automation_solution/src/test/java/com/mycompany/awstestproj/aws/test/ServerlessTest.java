package com.mycompany.awstestproj.aws.test;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.ListSubscriptionsByTopicResponse;
import software.amazon.awssdk.services.sns.model.PublishResponse;
import software.amazon.awssdk.services.sns.model.Topic;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ListQueuesResponse;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.*;

public class ServerlessTest {

    @Test
    public void testDynamoDBTableRequirements() {
        DynamoDbClient dynamoDb = DynamoDbClient.create();

        ListTablesResponse listTablesResponse = dynamoDb.listTables();

        String tableName = listTablesResponse.tableNames().stream()
                .filter(name -> name.contains("cloudxserverless-DatabaseImagesTable"))
                .findFirst()
                .orElse(null);
        assertNotNull(tableName, "DynamoDB table does not exist");

        DescribeTableResponse describeTableResponse;
        describeTableResponse = dynamoDb.describeTable(r -> r.tableName(tableName));

        assertTrue(describeTableResponse.table().globalSecondaryIndexes().isEmpty(), "Global secondary indexes are enabled");

        ProvisionedThroughputDescription provisionedThroughput = describeTableResponse.table().provisionedThroughput();
        assertEquals(5L, provisionedThroughput.readCapacityUnits(), "Provisioned read capacity units do not match");

        assertEquals(1L, provisionedThroughput.writeCapacityUnits(), "Provisioned write capacity units do not match");

        DescribeTimeToLiveResponse ttlResponse = dynamoDb.describeTimeToLive(r -> r.tableName(tableName));
        assertEquals(TimeToLiveStatus.DISABLED, ttlResponse.timeToLiveDescription().timeToLiveStatus(), "Time to Live is not disabled");

        // Check the table tags
        ListTagsOfResourceResponse tagsResponse = dynamoDb.listTagsOfResource(r -> r.resourceArn(describeTableResponse.table().tableArn()));
        boolean hasRequiredTag = tagsResponse.tags().stream()
                .anyMatch(tag -> tag.key().equals("cloudx") && tag.value().equals("qa"));
        assertTrue(hasRequiredTag, "Required tag is not present");




    }

    private static final String BASE_URL = "http://ec2-18-192-209-68.eu-central-1.compute.amazonaws.com";

    @Test
    public void testSavedImage() {

        File imageFile = new File("src/test/resources/images/download.jfif");

        RestAssured.baseURI = BASE_URL;

        given()
                .multiPart("upfile", imageFile)
                .when()
                .post("/api/image")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("id", notNullValue())
                .extract()
                .response();

        DynamoDbClient dynamoDb = DynamoDbClient.create();

        ListTablesResponse listTablesResponse = dynamoDb.listTables();

        String tableName = listTablesResponse.tableNames().stream()
                .filter(name -> name.contains("cloudxserverless-DatabaseImagesTable"))
                .findFirst()
                .orElse(null);

        ScanResponse scanResponse = dynamoDb.scan(r -> r.tableName(tableName));
        // Check if the items have the specified attributes
        List<String> requiredAttributes = Arrays.asList("created_at", "last_modified", "object_key", "object_size", "object_type");
        boolean itemsHaveRequiredAttributes = scanResponse.items().stream()
                .allMatch(item -> item.keySet().containsAll(requiredAttributes));
        assertTrue(itemsHaveRequiredAttributes, "Items do not have the required attributes");

    }

    @Test
    public void testSNSTopic() {

        SnsClient sns = SnsClient.create();

        String topicArn = sns.listTopics().topics().stream()
                .filter(topic -> topic.topicArn().contains("cloudxserverless-TopicSNSTopic"))
                .findFirst()
                .map(Topic::topicArn)
                .orElse(null);
        assertNotNull(topicArn, "SNS topic does not exist");

        SqsClient sqs = SqsClient.create();

        ListQueuesResponse listQueuesResponse = sqs.listQueues();

        String queueUrl = listQueuesResponse.queueUrls().stream()
                .filter(url -> url.contains("cloudxserverless-QueueSQSQueue"))
                .findFirst()
                .orElse(null);

        String queueArn = sqs.getQueueAttributes(r -> r.queueUrl(queueUrl).attributeNamesWithStrings("QueueArn"))
                .attributes().get("QueueArn");

        String subscriptionArn = sns.subscribe(r -> r.topicArn(topicArn).
                protocol("sqs").
                endpoint("arn:aws:sqs:eu-central-1:211125335876:cloudxserverless-QueueSQSQueueE7532512-4FsL4dCv4ZfW")).subscriptionArn();

        ListSubscriptionsByTopicResponse listSubscriptionsByTopicResponse = sns.listSubscriptionsByTopic(r -> r.topicArn(topicArn));

        boolean isSubscribed = listSubscriptionsByTopicResponse.subscriptions().stream()
                .anyMatch(subscription -> subscription.subscriptionArn().equals(subscriptionArn));
        assertTrue(isSubscribed, "Endpoint is not subscribed to the SNS topic");

        PublishResponse publishResponse = sns.publish(r -> r.topicArn(topicArn).message("This is a message"));

        assertNotNull(publishResponse.messageId(), "Message was not published");

        sns.unsubscribe(r -> r.subscriptionArn(subscriptionArn));
    }

}
