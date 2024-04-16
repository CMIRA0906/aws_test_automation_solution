package com.mycompany.awstestproj.aws.test;

import com.google.gson.Gson;
import com.mycompany.awstestproj.httpClient.HttpClient;
import com.mycompany.awstestproj.model.InstanceInfo;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class EC2DeploymentTest {

    private final Ec2Client ec2 = Ec2Client.create();

    @Test
    public void testPublicPrivateInstances() {
        DescribeInstancesRequest request = DescribeInstancesRequest.builder().build();
        DescribeInstancesResponse response = ec2.describeInstances(request);

        long publicInstances = response.reservations().stream().flatMap(reservation -> reservation.instances().stream()).flatMap(instance -> instance.networkInterfaces().stream()).filter(networkInterface -> networkInterface.association() != null && networkInterface.association().publicIp() != null).count();

        long privateInstances = response.reservations().stream().flatMap(reservation -> reservation.instances().stream()).flatMap(instance -> instance.networkInterfaces().stream()).filter(networkInterface -> networkInterface.association() == null || networkInterface.association().publicIp() == null).count();

        assertEquals(1, publicInstances, "There should be 1 public instance");
        assertEquals(1, privateInstances, "There should be 1 private instance");
    }

    @Test
    public void testInstanceConfiguration() {
        DescribeInstancesRequest request = DescribeInstancesRequest.builder().build();
        DescribeInstancesResponse response = ec2.describeInstances(request);

        response.reservations().forEach(reservation -> reservation.instances().forEach(instance -> Assertions.assertAll(() -> assertEquals(InstanceType.T2_MICRO, instance.instanceType(), "Instance type should be t2.micro"), () -> assertTrue(instance.tags().stream().anyMatch(tag -> tag.key().equals("cloudx") && tag.value().equals("qa")), "Instance should have tag cloudx:qa"), () -> {
            DescribeImagesRequest imagesRequest = DescribeImagesRequest.builder().imageIds(instance.imageId()).build();
            DescribeImagesResponse imagesResponse = ec2.describeImages(imagesRequest);
            assertTrue(imagesResponse.images().get(0).platformDetails().contains("Amazon Linux 2"), "Instance OS should be Amazon Linux 2,but what it got is: " + imagesResponse.images().get(0).platformDetails());
        }, () -> {
            DescribeVolumesRequest volumesRequest = DescribeVolumesRequest.builder().volumeIds(instance.blockDeviceMappings().get(0).ebs().volumeId()).build();
            DescribeVolumesResponse volumesResponse = ec2.describeVolumes(volumesRequest);
            assertEquals(Integer.valueOf(8), volumesResponse.volumes().get(0).size(), "Root block device size should be 8 GB");
        }, () -> {
            if (instance.networkInterfaces().get(0).association() != null) {
                assertNotNull(instance.networkInterfaces().get(0).association().publicIp(), "Public instance should have public IP assigned");
            } else {
                assertNull(instance.networkInterfaces().get(0).association(), "Private instance should not have public IP assigned");
            }
        })));
    }

    @Test
    public void testSecurityGroupsConfiguration() {
        DescribeInstancesRequest request = DescribeInstancesRequest.builder().build();
        DescribeInstancesResponse response = ec2.describeInstances(request);

        response.reservations().forEach(reservation -> reservation.instances().forEach(instance -> instance.securityGroups().forEach(securityGroup -> {
            DescribeSecurityGroupsRequest securityGroupsRequest = DescribeSecurityGroupsRequest.builder().groupIds(securityGroup.groupId()).build();
            DescribeSecurityGroupsResponse securityGroupsResponse = ec2.describeSecurityGroups(securityGroupsRequest);

            securityGroupsResponse.securityGroups().forEach(sg -> sg.ipPermissions().forEach(ipPermission -> Assertions.assertAll(() -> {
                if (instance.networkInterfaces().get(0).association() != null) {
                    assertTrue(ipPermission.fromPort() == 22 || ipPermission.fromPort() == 80, "The public instance should be accessible from the internet by SSH (port 22) and HTTP (port 80) only");
                } else {
                    assertTrue(ipPermission.fromPort() == 22 || ipPermission.fromPort() == 80, "The private instance should be accessible only from the public instance by SSH and HTTP protocols only");
                }
            }, () -> {
                ipPermission.ipRanges().forEach(ipRange -> {
                    String cidrIp = ipRange.cidrIp();
                    assertNotNull(cidrIp, "CIDR IP should not be null");
                    assertTrue(cidrIp.matches("0.0.0.0/0"), "CIDR IP should be in valid format2");
                });
            })));
        })));
    }

    @Test
    public void testPrivateInstanceInfoEndpointAfterDeploying() {

        Filter filter = Filter.builder().name("network-interface.addresses.private-ip-address").values("*.*.*.*") // Matches any IP address
                .build();
        DescribeInstancesRequest request = DescribeInstancesRequest.builder().filters(filter).build();

        DescribeInstancesResponse response = ec2.describeInstances(request);

        for (Reservation reservation : response.reservations()) {
            for (Instance instance : reservation.instances()) {
                String availabilityZone = instance.placement().availabilityZone();
                String region = instance.placement().availabilityZone().substring(0, availabilityZone.length() - 1);

                Assertions.assertAll(
                        () -> assertNotNull(instance.placement().availabilityZone(), "Availability zone should not be null "),
                        () -> assertNotNull(instance.privateIpAddress(), "Private IPv4 should be " + instance.privateIpAddress()),
                        () -> assertNotNull(region, "Region should not be null"));
            }
        }

    }

    @Test
    public void testPublicInstanceInfoEndpointAfterDeploying() throws IOException {
        CloseableHttpResponse clientResponse;
        Filter filter = Filter.builder().name("ip-address")
                .values("*.*.*.*").build();

        DescribeInstancesRequest request = DescribeInstancesRequest.builder().filters(filter).build();

        DescribeInstancesResponse response = ec2.describeInstances(request);

        for (Reservation reservation : response.reservations()) {
            for (Instance instance : reservation.instances()) {

                String apiEndpoint = "http://" + instance.publicDnsName() + "/";

                clientResponse = HttpClient.buildHttpClientGet(apiEndpoint);
                assertEquals(200, clientResponse.getStatusLine().getStatusCode(), "Response status code should be 200");
                String responseBody = EntityUtils.toString(clientResponse.getEntity());
                System.out.println(responseBody);
                Gson gson = new Gson();
                InstanceInfo instanceInfo = gson.fromJson(responseBody, InstanceInfo.class);

                String availabilityZone = instance.placement().availabilityZone();
                String region = instance.placement().availabilityZone().substring(0, availabilityZone.length() - 1);

                Assertions.assertAll(
                        () -> assertEquals(instance.placement().availabilityZone(), instanceInfo.getAvailability_zone(), "Availability zone should be availabilityZone"),
                        () -> assertEquals(instance.privateIpAddress(), instanceInfo.getPrivate_ipv4(), "Private IPv4 should be " + instance.privateIpAddress()),
                        () -> assertEquals(region, instanceInfo.getRegion(), "Region should be " + region));

            }
        }

    }


}
