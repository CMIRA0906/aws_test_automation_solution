package com.mycompany.awstestproj.aws.test;

import org.junit.jupiter.api.BeforeAll;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.iam.IamClient;

public class BaseClass {

    protected static IamClient iam;

    @BeforeAll
    public static void setUp() {
        iam = IamClient.builder().region(Region.AWS_GLOBAL).build();
    }

}
