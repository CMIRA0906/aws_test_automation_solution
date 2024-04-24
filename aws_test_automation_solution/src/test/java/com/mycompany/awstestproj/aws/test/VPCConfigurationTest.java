package com.mycompany.awstestproj.aws.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class VPCConfigurationTest {

    private final Ec2Client ec2 = Ec2Client.create();
    private final DescribeInstancesResponse instancesResult = ec2.describeInstances();

    @Test
    public void testVPCConfiguration() {
        DescribeVpcsResponse vpcsResult = ec2.describeVpcs();
        boolean hasBothPublicAndPrivate = vpcsResult.vpcs()
                .stream().anyMatch(vpc -> {
                    List<Subnet> subnets = ec2.describeSubnets(
                                    DescribeSubnetsRequest.builder()
                                            .filters(
                                                    Filter.builder()
                                                            .name("vpc-id")
                                                            .values(vpc.vpcId())
                                                            .build()
                                            ).build())
                            .subnets();
                    long publicCount = subnets.stream().filter(Subnet::mapPublicIpOnLaunch).count();
                    long privateCount = subnets.size() - publicCount;
                    return publicCount == 1 && privateCount == 1;
                });

        assertTrue(hasBothPublicAndPrivate, "There should be at least one VPC with both public and private subnets");
    }

    @Test
    public void testSubNetConfiguration() {

        String vpcApplicationName = "cloudxinfo/Network/Vpc";

        DescribeVpcsRequest vpcsRequest = DescribeVpcsRequest.builder()
                .filters(Filter.builder()
                        .name("tag:Name")
                        .values(vpcApplicationName)
                        .build())
                .build();

        DescribeVpcsResponse vpcsResult = ec2.describeVpcs(vpcsRequest);
        boolean hasBothPublicAndPrivate = vpcsResult.vpcs()
                .stream().anyMatch(vpc -> {
                    List<Subnet> subnets = ec2.describeSubnets(
                                    DescribeSubnetsRequest.builder()
                                            .filters(
                                                    Filter.builder()
                                                            .name("vpc-id")
                                                            .values(vpc.vpcId())
                                                            .build()
                                            ).build())
                            .subnets();
                    long publicCount = subnets.stream().filter(Subnet::mapPublicIpOnLaunch).count();
                    long privateCount = subnets.size() - publicCount;
                    return publicCount == 1 && privateCount == 1;
                });
        Assertions.assertAll(
                () -> assertTrue(hasBothPublicAndPrivate, "There should be one VPC with both public and private subnets"),
                () -> assertEquals(1, vpcsResult.vpcs().size(), "There should be one created VPC"),
                () -> assertEquals(false, vpcsResult.vpcs().get(0).isDefault(), "The created VPC should be non default"),
                () -> assertEquals("10.0.0.0/16", vpcsResult.vpcs().get(0).cidrBlock(), "The created VPC CIDR Block should be: 10.0.0.0/16")
        );
    }

    @Test
    public void testPublicInstanceAccessibility() {

        List<Instance> instances = instancesResult.reservations().stream()
                .flatMap(reservation -> reservation.instances().stream())
                .filter(instance -> instance.publicIpAddress() != null)
                .toList();

        for (Instance instance : instances) {
            DescribeSubnetsRequest subnetsRequest = DescribeSubnetsRequest.builder()
                    .subnetIds(instance.subnetId())
                    .build();
            Subnet subnet = ec2.describeSubnets(subnetsRequest).subnets().get(0);

            DescribeRouteTablesRequest routeTablesRequest = DescribeRouteTablesRequest.builder()
                    .filters(Filter.builder()
                            .name("association.subnet-id")
                            .values(subnet.subnetId())
                            .build())
                    .build();

            List<RouteTable> routeTables = ec2.describeRouteTables(routeTablesRequest).routeTables();

            boolean hasInternetGateway = routeTables.stream().flatMap(routeTable -> routeTable.routes().stream())
                    .anyMatch(route -> route.gatewayId() != null && route.gatewayId().startsWith("igw-"));

            assertTrue(hasInternetGateway, "The public instance should be accessible from the internet");

        }
    }

    @Test
    public void testInstanceAccessibility() {

        List<Instance> instances = instancesResult.reservations().stream()
                .flatMap(reservation -> reservation.instances().stream())
                .toList();

        for (Instance publicInstance : instances) {
            if (publicInstance.publicIpAddress() == null) {
                continue;
            }

            for (Instance privateInstance : instances) {
                if (privateInstance.publicIpAddress() != null) {
                    continue;
                }

                // Check if the public instance and the private instance are part of the same security group
                boolean inSameSecurityGroup = publicInstance.securityGroups().stream()
                        .anyMatch(publicGroup -> privateInstance.securityGroups().stream()
                                .anyMatch(privateGroup -> privateGroup.groupId().equals(publicGroup.groupId())));

                assertTrue(inSameSecurityGroup, "The public instance should have access to the private instance");

                // Check if the security group allows all traffic within itself
                for (GroupIdentifier group : publicInstance.securityGroups()) {
                    DescribeSecurityGroupsRequest securityGroupsRequest = DescribeSecurityGroupsRequest.builder()
                            .groupNames(group.groupId())
                            .build();
                    SecurityGroup securityGroup = ec2.describeSecurityGroups(securityGroupsRequest).securityGroups().get(0);

                    boolean allowsAllTraffic = securityGroup.ipPermissions().stream()
                            .anyMatch(permission -> permission.ipProtocol().equals("-1") && permission.userIdGroupPairs().stream()
                                    .anyMatch(pair -> pair.groupId().equals(group.groupId())));
                    assertTrue(allowsAllTraffic, "The security group should allow all traffic within itself");
                }
            }
        }
    }

    @Test
    public void testPrivateInstanceInternetAccess() {

        List<Instance> instances = instancesResult.reservations().stream()
                .flatMap(reservation -> reservation.instances().stream())
                .filter(instance -> instance.publicIpAddress() == null)
                .toList();

        for (Instance instance : instances) {
            DescribeSubnetsRequest subnetsRequest = DescribeSubnetsRequest.builder()
                    .subnetIds(instance.subnetId())
                    .build();
            Subnet subnet = ec2.describeSubnets(subnetsRequest).subnets().get(0);

            DescribeRouteTablesRequest routeTablesRequest = DescribeRouteTablesRequest.builder()
                    .filters(Filter.builder()
                            .name("association.subnet-id").values(subnet.subnetId())
                            .build())
                    .build();
            List<RouteTable> routeTables = ec2.describeRouteTables(routeTablesRequest).routeTables();

            boolean hasNatGateway = routeTables.stream().flatMap(routeTable -> routeTable.routes().stream())
                    .anyMatch(route -> route.natGatewayId() != null);

            assertTrue(hasNatGateway, "The private instance should have access to the internet via a NAT Gateway");
        }
    }

    @Test
    public void testPrivateInstanceInaccessibility() {

        List<Instance> instances = instancesResult.reservations().stream()
                .flatMap(reservation -> reservation.instances().stream())
                .filter(instance -> instance.publicIpAddress() == null)
                .toList();

        for (Instance instance : instances) {
            assertNull(instance.publicIpAddress(), "The private instance should not have a public IP address");

            for (GroupIdentifier group : instance.securityGroups()) {
                DescribeSecurityGroupsRequest securityGroupsRequest = DescribeSecurityGroupsRequest.builder()
                        .groupIds(group.groupId())
                        .build();

                SecurityGroup securityGroup = ec2.describeSecurityGroups(securityGroupsRequest).securityGroups().get(0);

                boolean allowsInboundTraffic = securityGroup.ipPermissions().stream()
                        .anyMatch(permission -> permission.ipRanges().stream()
                                .anyMatch(range -> range.cidrIp().equals("0.0.0.0/0")));

                assertFalse(allowsInboundTraffic, "The security group should not allow inbound traffic from the public internet");
            }
        }
    }

}
