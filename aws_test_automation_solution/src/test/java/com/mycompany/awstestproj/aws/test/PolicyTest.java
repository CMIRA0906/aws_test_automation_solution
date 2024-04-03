package com.mycompany.awstestproj.aws.test;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mycompany.awstestproj.model.CustomPolicy;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.GetPolicyResponse;
import software.amazon.awssdk.services.iam.model.GetPolicyVersionResponse;
import software.amazon.awssdk.services.iam.model.ListPoliciesResponse;
import software.amazon.awssdk.services.iam.model.Policy;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PolicyTest extends BaseClass {


    @ParameterizedTest
    @MethodSource("com.mycompany.awstestproj.aws.dataprovider.TestData#policyProvider")
    public void policyNamesExistTest(CustomPolicy policy) {

        ListPoliciesResponse response = iam.listPolicies();
        List<String> policyNames = response.policies().stream().map(Policy::policyName).collect(Collectors.toList());
        assertTrue(policyNames.contains(policy.getPolicyName()));

    }

    @ParameterizedTest
    @MethodSource("com.mycompany.awstestproj.aws.dataprovider.TestData#policyProvider")
    public void policiesStatementTest(CustomPolicy policy) throws UnsupportedEncodingException {
        IamClient iam = IamClient.builder().region(Region.AWS_GLOBAL).build();

        String policyArn = "arn:aws:iam::211125335876:policy/" + policy.getPolicyName();

        GetPolicyResponse getPolicyResponse = iam.getPolicy(b -> b.policyArn(policyArn));

        String defaultVersionId = getPolicyResponse.policy().defaultVersionId();

        GetPolicyVersionResponse policyVersion = iam.getPolicyVersion(pvb -> pvb.policyArn(policyArn).versionId(defaultVersionId));

        String policyDocument = policyVersion.policyVersion().document();
        policyDocument = java.net.URLDecoder.decode(policyDocument, "UTF-8");
        Gson gson = new Gson();
        JsonElement jsonElement = gson.fromJson(policyDocument, JsonElement.class);
        JsonObject jsonObject = jsonElement.getAsJsonObject();

        JsonArray statements = jsonObject.getAsJsonArray("Statement");

        for (JsonElement statementElement : statements) {
            JsonObject statementObject = statementElement.getAsJsonObject();
            String permission = statementObject.get("Action").getAsString();
            String resources = statementObject.get("Resource").getAsString();
            String effect = statementObject.get("Effect").getAsString();
            // Assertions for permission, Action, Resources exist in the policyDocument
            assertEquals(policy.getResources(), resources, "The resources are different in IAM");
            assertEquals(policy.getEffect(), effect, "Effect is not the expected");
            assertEquals(policy.getActions(), permission, "permissions are not the expected");
        }
    }
}

