package com.mycompany.awstestproj.aws.test;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.ListSubscriptionsByTopicResponse;
import software.amazon.awssdk.services.sns.model.ListTopicsResponse;
import software.amazon.awssdk.services.sns.model.Subscription;
import software.amazon.awssdk.services.sns.model.Topic;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SNSTest {

    @Test
    public void testApplicationInstanceRequirements() {

        SnsClient sns = SnsClient.create();

        // List the SNS topics
        ListTopicsResponse listTopicsResponse = sns.listTopics();

        // Find the SNS topic with the known part of the name
        Optional<Topic> topicOptional = listTopicsResponse.topics().stream()
                .filter(t -> t.topicArn().contains("cloudximage"))
                .findFirst();
        assertTrue( topicOptional.isPresent(),"SNS topic not found");

        // List the subscriptions for the SNS topic
        ListSubscriptionsByTopicResponse listSubscriptionsByTopicResponse = sns.listSubscriptionsByTopic(s -> s.topicArn(topicOptional.get().topicArn()));
        List<Subscription> subscriptions = listSubscriptionsByTopicResponse.subscriptions();

        // Check if there are any subscriptions
        assertFalse(subscriptions.isEmpty(),"There are no subscriptions for the SNS topic");

        // Check if all the subscriptions are confirmed
        boolean areAllSubscriptionsConfirmed = subscriptions.stream()
                .allMatch(s -> s.subscriptionArn() != null && !s.subscriptionArn().equals("PendingConfirmation"));
        assertTrue(areAllSubscriptionsConfirmed,"Not all subscriptions for the SNS topic are confirmed");

        // Check if all the subscriptions are email subscriptions
        boolean areAllSubscriptionsEmailSubscriptions = subscriptions.stream()
                .allMatch(s -> s.protocol().equals("email"));
        assertTrue( areAllSubscriptionsEmailSubscriptions,"Not all subscriptions for the SNS topic are email subscriptions");
    }

}
