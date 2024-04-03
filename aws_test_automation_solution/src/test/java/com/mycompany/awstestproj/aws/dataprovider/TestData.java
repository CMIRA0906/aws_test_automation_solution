package com.mycompany.awstestproj.aws.dataprovider;

import com.mycompany.awstestproj.model.CustomPolicy;
import org.junit.jupiter.params.provider.Arguments;

import java.util.stream.Stream;

public class TestData {

    private static Stream<CustomPolicy> policyProvider() {
        return Stream.of(
                new CustomPolicy("FullAccessPolicyEC2", "ec2:*", "*", "Allow"),
                new CustomPolicy("FullAccessPolicyS3", "s3:*", "*", "Allow"),
                new CustomPolicy("ReadAccessPolicyS3", "s3:Describe*, s3:Get*, s3:List*", "All", "Allow"));
    }

    private static Stream<Arguments> rolePolicyProvider() {
        return Stream.of(
                Arguments.of("FullAccessRoleEC2", "FullAccessPolicyEC2"),
                Arguments.of("FullAccessRoleS3", "FullAccessPolicyS3"),
                Arguments.of("ReadAccessRoleS3","ReadAccessPolicyS3")
        );
    }

    private static Stream<Arguments> groupPolicyProvider() {
        return Stream.of(
                Arguments.of("FullAccessGroupEC2", "FullAccessPolicyEC2"),
                Arguments.of("FullAccessGroupS3", "FullAccessPolicyS3"),
                Arguments.of("ReadAccessGroupS3", "ReadAccessPolicyS3")
        );
    }

    private static Stream<Arguments> userGroupProvider() {
        return Stream.of(
                Arguments.of("FullAccessUserEC2", "FullAccessGroupEC2"),
                Arguments.of("FullAccessUserS3", "FullAccessGroupS3"),
                Arguments.of("ReadAccessUserS3", "ReadAccessGroupS3")
        );
    }
}
