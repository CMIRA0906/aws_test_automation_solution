package com.mycompany.awstestproj.httpClient;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.IOException;

public class HttpClient {

    private HttpClient(){

    }

    public static CloseableHttpResponse buildHttpClientGet(String apiEndpoint){
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(apiEndpoint);
        try {
            CloseableHttpResponse clientResponse = httpClient.execute(httpGet);
            return clientResponse;

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
