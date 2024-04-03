package com.mycompany.awstestproj.aws.test;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.awssdk.services.iam.model.AttachedPolicy;
import software.amazon.awssdk.services.iam.model.ListAttachedRolePoliciesResponse;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RolTest extends BaseClass {

    @ParameterizedTest
    @MethodSource("com.mycompany.awstestproj.aws.dataprovider.TestData#rolePolicyProvider")
    public void attachedPolicyToRoleTest(String role, String policy) {

        boolean isPolicyAttachedToRole = false;
        ListAttachedRolePoliciesResponse response = iam.listAttachedRolePolicies(r -> r.roleName(role));
        assertNotNull(response, "The ListAttachedRolePoliciesResponse is null");
        for (AttachedPolicy attachedPolicy : response.attachedPolicies()) {
            if (attachedPolicy.policyName().equalsIgnoreCase(policy)) {
                isPolicyAttachedToRole = true;
                break;
            }
        }
        assertTrue(isPolicyAttachedToRole, "The policy is not attached to the role");

    }


}
