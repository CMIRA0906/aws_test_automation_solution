package com.mycompany.awstestproj.aws.test;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class S3ImagesTest {

    private static final String BASE_URL = "http://ec2-18-192-126-186.eu-central-1.compute.amazonaws.com";

    @Test
    public void testUploadImage() {

        File imageFile = new File("src/test/resources/images/download.jfif");

        RestAssured.baseURI = BASE_URL;

        given()
                .multiPart("upfile", imageFile)
                .when()
                .post("/api/image")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("id", notNullValue())
                .extract()
                .response();

    }

    @Test
    public void testDownloadImage() {

        RestAssured.baseURI = BASE_URL;

        Response  getResponse = given()
                .when()
                .get("/api/image")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .response();
        List<Object> imageIdsList = JsonPath.from(getResponse.asString()).getList("id");
        assertFalse(imageIdsList.isEmpty(), "There are not images in the bucket");
        //Download an image
        given()
                .when()
                .get("/api/image/file/".concat(imageIdsList.get(0).toString()))
                .then()
                .statusCode(200)
                .contentType("image/jfif")
                .extract()
                .response();

    }

    @Test
    public void testDeleteImage() {

        RestAssured.baseURI = BASE_URL;

        Response  getResponse = given()
                .when()
                .get("/api/image")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .response();
        List<Object> imageIdsList = JsonPath.from(getResponse.asString()).getList("id");
        assertFalse(imageIdsList.isEmpty(), "There are not images in the bucket");
        //Download an image
        given()
                .when()
                .delete("/api/image/".concat(imageIdsList.get(0).toString()))
                .then()
                .statusCode(200)
                .body(containsString("Image is deleted"));
    }


}


