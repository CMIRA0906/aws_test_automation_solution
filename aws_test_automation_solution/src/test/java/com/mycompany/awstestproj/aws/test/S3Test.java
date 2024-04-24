package com.mycompany.awstestproj.aws.test;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.iam.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.model.Tag;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class S3Test extends BaseClass{

    private final Ec2Client ec2 = Ec2Client.create();

    @Test
    public void testInstanceRequirements() {

        DescribeInstancesRequest request = DescribeInstancesRequest.builder().build();
        DescribeInstancesResponse response = ec2.describeInstances(request);
        String expectedInstanceName = "cloudximage";

        Optional<Instance> instanceOptional = response.reservations().stream()
                .flatMap(reservation -> reservation.instances().stream()).
                filter(instance -> instance.tags().stream().
                        anyMatch(tag -> tag.key().equals("Name") && tag.value().contains(expectedInstanceName)))
                .filter(instance -> instance.publicIpAddress() != null).findFirst();

        assertTrue(instanceOptional.isPresent(), "The instance " + expectedInstanceName + " does not exist or is not in a public subnet.");

        Instance instance = instanceOptional.get();
        String iamInstanceProfileArn = instance.iamInstanceProfile().arn();
        List<GroupIdentifier> securityGroups = instance.securityGroups();

        boolean isAccessibleBySSH = securityGroups.stream().map(GroupIdentifier::groupId).anyMatch(groupId -> {
            DescribeSecurityGroupsRequest describeSecurityGroupsRequest = DescribeSecurityGroupsRequest.builder().groupIds(groupId).build();
            DescribeSecurityGroupsResponse describeSecurityGroupsResponse = ec2.describeSecurityGroups(describeSecurityGroupsRequest);

            return describeSecurityGroupsResponse.securityGroups().stream().flatMap(securityGroup -> securityGroup.ipPermissions().stream()).anyMatch(ipPermission -> ipPermission.ipProtocol().equals("tcp") && ipPermission.fromPort() == 22 && ipPermission.toPort() == 22);
        });

        assertTrue(isAccessibleBySSH, "The instance is not accessible by SSH.");
        assertNotNull(iamInstanceProfileArn, "The instance does not have an IAM role attached.");

    }

    @Test
    public void testS3BucketAccessViaIamRole() {
        DescribeInstancesRequest request = DescribeInstancesRequest.builder().build();
        DescribeInstancesResponse response = ec2.describeInstances(request);
        String expectedInstanceName = "cloudximage";

        Optional<Instance> instanceOptional = response.reservations().stream()
                .flatMap(reservation -> reservation.instances().stream())
                .filter(instance -> instance.tags().stream()
                        .anyMatch(tag -> tag.key().equals("Name") && tag.value().contains(expectedInstanceName)))
                .filter(instance -> instance.publicIpAddress() != null).findFirst();

        assertTrue(instanceOptional.isPresent(), "The instance " + expectedInstanceName + " does not exist or is not in a public subnet.");

        Instance instance = instanceOptional.get();
        String iamInstanceProfileArn = instance.iamInstanceProfile().arn();

        // Extract the instance profile name from the ARN
        String instanceProfileName = iamInstanceProfileArn.substring(iamInstanceProfileArn.lastIndexOf('/') + 1);

        // Get the instance profile
        GetInstanceProfileRequest getInstanceProfileRequest = GetInstanceProfileRequest.builder().instanceProfileName(instanceProfileName).build();
        GetInstanceProfileResponse getInstanceProfileResponse = iam.getInstanceProfile(getInstanceProfileRequest);

        // Get the role from the instance profile
        String roleName = getInstanceProfileResponse.instanceProfile().roles().get(0).roleName();

        ListAttachedRolePoliciesRequest listAttachedRolePoliciesRequest = ListAttachedRolePoliciesRequest.builder()
                .roleName(roleName)
                .build();
        boolean hasS3Access = iam.listAttachedRolePolicies(listAttachedRolePoliciesRequest).attachedPolicies().stream().anyMatch(policy -> {
            // Get the policy
            GetPolicyRequest getPolicyRequest = GetPolicyRequest.builder()
                    .policyArn(policy.policyArn())
                    .build();
            String defaultVersionId = iam.getPolicy(getPolicyRequest).policy().defaultVersionId();

            // Get the policy version
            GetPolicyVersionRequest getPolicyVersionRequest = GetPolicyVersionRequest.builder().policyArn(policy.policyArn()).versionId(defaultVersionId).build();
            GetPolicyVersionResponse getPolicyVersionResponse = iam.getPolicyVersion(getPolicyVersionRequest);

            // Check if the policy allows access to the S3 bucket
            String policyDocument = getPolicyVersionResponse.policyVersion().document();
            try {
                policyDocument = URLDecoder.decode(policyDocument, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }

            return policyDocument.contains("\"Action\":\"s3:ListBucket\"") &&
                    policyDocument.contains("\"Resource\":\"arn:aws:s3:::cloudximage-imagestorebucket");

        });
        assertTrue(hasS3Access, "The application does not have access to the S3 bucket via the IAM role.");
    }

    @Test
    public void testBucketRequirements() {

        S3Client s3 = S3Client.create();

        Optional<Bucket> bucketOptional = s3.listBuckets().buckets().stream()
                .filter(bucket -> bucket.name().startsWith("cloudximage-imagestorebucket"))
                .findFirst();
        assertTrue(bucketOptional.isPresent(), "The required bucket does not exist.");

        Bucket bucket = bucketOptional.get();

        GetBucketTaggingRequest getBucketTaggingRequest = GetBucketTaggingRequest.builder().bucket(bucket.name()).build();
        List<Tag> tags = s3.getBucketTagging(getBucketTaggingRequest).tagSet();

        boolean hasRequiredTag = tags.stream().anyMatch(tag -> tag.key().equals("cloudx") && tag.value().equals("qa"));
        assertTrue(hasRequiredTag, "The bucket does not have the required tag.");

        GetBucketEncryptionRequest getBucketEncryptionRequest = GetBucketEncryptionRequest.builder().bucket(bucket.name()).build();
        List<ServerSideEncryptionRule> encryptionRules = s3.getBucketEncryption(getBucketEncryptionRequest).serverSideEncryptionConfiguration().rules();

        boolean usesSSES3Encryption = encryptionRules.stream().anyMatch(rule -> {
            ServerSideEncryptionByDefault defaultEncryption = rule.applyServerSideEncryptionByDefault();
            return defaultEncryption != null && "AES256".equals(defaultEncryption.sseAlgorithmAsString());
        });
        assertTrue(usesSSES3Encryption, "The bucket does not use SSE-S3 encryption.");

        GetBucketVersioningRequest getBucketVersioningRequest = GetBucketVersioningRequest.builder().bucket(bucket.name()).build();
        GetBucketVersioningResponse getBucketVersioningResponse = s3.getBucketVersioning(getBucketVersioningRequest);

        String versioningStatus = getBucketVersioningResponse.statusAsString();
        assertFalse("Enabled".equals(versioningStatus), "The bucket versioning is not disabled.");

        // Get the bucket public access block configuration
        GetPublicAccessBlockRequest getPublicAccessBlockRequest = GetPublicAccessBlockRequest.builder().bucket(bucket.name()).build();
        GetPublicAccessBlockResponse getPublicAccessBlockResponse = s3.getPublicAccessBlock(getPublicAccessBlockRequest);

        // Check if the bucket has public blocked access
        PublicAccessBlockConfiguration publicAccessBlockConfiguration = getPublicAccessBlockResponse.publicAccessBlockConfiguration();
        boolean isBlockedToPublicAccess = publicAccessBlockConfiguration.blockPublicAcls()
                || publicAccessBlockConfiguration.blockPublicPolicy()
                || publicAccessBlockConfiguration.ignorePublicAcls()
                || publicAccessBlockConfiguration.restrictPublicBuckets();
        assertTrue(isBlockedToPublicAccess, "The bucket has public access.");

    }

}


