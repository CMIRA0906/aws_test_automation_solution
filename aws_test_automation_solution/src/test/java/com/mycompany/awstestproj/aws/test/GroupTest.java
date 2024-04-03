package com.mycompany.awstestproj.aws.test;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class GroupTest extends BaseClass{

    @ParameterizedTest
    @MethodSource("com.mycompany.awstestproj.aws.dataprovider.TestData#groupPolicyProvider")
    public void groupPolicyTest(String group, String policy) {
        boolean isPolicyAttachedToGroup = iam.listAttachedGroupPolicies(r -> r.groupName(group))
                .attachedPolicies().stream()
                .anyMatch(attachedPolicy -> attachedPolicy.policyName().equalsIgnoreCase(policy));

        assertTrue(isPolicyAttachedToGroup, "The policy is not attached to the group");
    }


}
