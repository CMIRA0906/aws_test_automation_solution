package com.mycompany.awstestproj.aws.test;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeSecurityGroupsRequest;
import software.amazon.awssdk.services.ec2.model.DescribeSecurityGroupsResponse;
import software.amazon.awssdk.services.ec2.model.IpPermission;
import software.amazon.awssdk.services.ec2.model.SecurityGroup;
import software.amazon.awssdk.services.rds.RdsClient;
import software.amazon.awssdk.services.rds.model.DBInstance;
import software.amazon.awssdk.services.rds.model.DescribeDbInstancesResponse;
import software.amazon.awssdk.services.rds.model.VpcSecurityGroupMembership;

import java.io.File;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

public class RDSTest {

    private static final String IMAGE_PATH = "src/test/resources/images/download.jfif";
    private static final String UPLOAD_ENDPOINT = "/api/image";
    private static final int STATUS_OK = 200;
    private static final String BASE_URL = "http://ec2-18-192-214-100.eu-central-1.compute.amazonaws.com";
    private static RdsClient rdsClient;

    private int uploadedImageId;

    @BeforeAll
    public static void setup() {
        RestAssured.baseURI = BASE_URL;
        rdsClient = RdsClient.create();
    }



    @Test
    public void testDBInstanceDeploymentInPrivateSubnet() {

        DescribeDbInstancesResponse describeDbInstancesResponse = rdsClient.describeDBInstances();
        DBInstance dbInstance = describeDbInstancesResponse.dbInstances().get(0);

        // Check if the RDS instance is in a VPC
        String vpcId = dbInstance.dbSubnetGroup().vpcId();
        assertFalse(vpcId == null || vpcId.isEmpty(), "The RDS instance is not in a VPC.");

        // Create an EC2 client
        Ec2Client ec2 = Ec2Client.create();
        // Describe the VPC security groups associated with the RDS instance

        for (VpcSecurityGroupMembership vpcSecurityGroupMembership : dbInstance.vpcSecurityGroups()) {
            DescribeSecurityGroupsRequest describeSecurityGroupsRequest = DescribeSecurityGroupsRequest.builder()
                    .groupIds(vpcSecurityGroupMembership.vpcSecurityGroupId())
                    .build();
            DescribeSecurityGroupsResponse describeSecurityGroupsResponse = ec2.describeSecurityGroups(describeSecurityGroupsRequest);
            SecurityGroup securityGroup = describeSecurityGroupsResponse.securityGroups().get(0);

            // Check if the security group allows inbound traffic from the public internet
            for (IpPermission ipPermission : securityGroup.ipPermissions()) {
                assertThrows(IndexOutOfBoundsException.class, () -> ipPermission.ipRanges().get(0).cidrIp(), "The RDS instance is accessible from the public internet.");
            }
        }
    }

    @Test
    public void testRDSInstanceRequirements() {
        // Create an RDS client
        RdsClient rds = RdsClient.create();

        // Describe the RDS instance
        DescribeDbInstancesResponse describeDbInstancesResponse = rds.describeDBInstances();
        DBInstance dbInstance = describeDbInstancesResponse.dbInstances().get(0);

        // Check requirements
        assertEquals("db.t3.micro", dbInstance.dbInstanceClass(), "Instance type does not match");
        assertFalse(dbInstance.multiAZ(), "Multi-AZ configuration does not match");
        assertEquals(100, dbInstance.allocatedStorage().intValue(), "Storage size does not match");
        assertEquals("gp2", dbInstance.storageType(), "Storage type does not match");
        assertFalse(dbInstance.storageEncrypted(), "Encryption status does not match");
        assertEquals("mysql", dbInstance.engine(), "Database type does not match");
        assertEquals("8.0.32", dbInstance.engineVersion(), "Database version does not match");
    }



    @Test
    public void testUploadImage() {
        File imageFile = new File("src/test/resources/images/download.jfif");

        Response uploadImageResponse = given()
                .multiPart("upfile", imageFile)
                .when()
                .post(UPLOAD_ENDPOINT)
                .then()
                .statusCode(STATUS_OK)
                .contentType(ContentType.JSON)
                .body("id", notNullValue())
                .extract()
                .response();

        uploadedImageId  = uploadImageResponse.jsonPath().getInt("id");
        given()
                .when()
                .get(UPLOAD_ENDPOINT + "/" + uploadedImageId)
                .then()
                .statusCode(STATUS_OK)
                .contentType(ContentType.JSON)
                    .body("id", notNullValue(),
                        "last_modified", notNullValue(),
                        "object_key", notNullValue(),
                        "object_size", instanceOf(Integer.class),
                        "object_type", notNullValue());
    }

    @Test
    public void testDeleteImage() {
        File imageFile = new File("src/test/resources/images/download.jfif");

        Response uploadImageResponse = given()
                .multiPart("upfile", imageFile)
                .when()
                .post(UPLOAD_ENDPOINT)
                .then()
                .statusCode(STATUS_OK)
                .contentType(ContentType.JSON)
                .body("id", notNullValue())
                .extract()
                .response();
        uploadedImageId  = uploadImageResponse.jsonPath().getInt("id");

        given()
                .when()
                .delete(UPLOAD_ENDPOINT + "/" + uploadedImageId)
                .then()
                .statusCode(STATUS_OK)
                .contentType(ContentType.JSON)
                .body(containsString("Image is deleted"))
                .extract()
                .response();

        given()
                .when()
                .get(UPLOAD_ENDPOINT + "/" + uploadedImageId)
                .then()
                .statusCode(HttpStatus.SC_NOT_FOUND)
                .contentType(ContentType.JSON);


    }

}
