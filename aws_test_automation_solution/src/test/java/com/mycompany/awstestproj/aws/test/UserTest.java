package com.mycompany.awstestproj.aws.test;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class UserTest extends BaseClass{

    @ParameterizedTest
    @MethodSource("com.mycompany.awstestproj.aws.dataprovider.TestData#userGroupProvider")
    public void userGroupTest(String user, String group) {
        boolean isUserInGroup = iam.listGroupsForUser(r -> r.userName(user))
                .groups().stream()
                .anyMatch(userGroup -> userGroup.groupName().equalsIgnoreCase(group));

        assertTrue(isUserInGroup, "The user does not belong to the expected group");
    }
}
