package com.runscope.jenkins.Runscope;

import java.io.IOException;
import java.io.PrintStream;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 * RunscopeTrigger
 *
 * @email help@runscope.com
 */
public class RunscopeTrigger implements Callable<String> {

    private static final String SCHEME = "https";
    private static final String API_HOST = "api.runscope.com";
    private static final String RUNSCOPE_HOST = "www.runscope.com";
    private static final String TEST_TRIGGER = "trigger";
    private static final String TEST_RESULTS = "results";
    private static final String TEST_RESULTS_PASS = "pass";
    private static final String TEST_RESULTS_WORKING = "working";
    private static final String TEST_RESULTS_QUEUED = "queued";
    private static final String TEST_RESULTS_FAIL = "fail";

    private final String accessToken;
    private String url;
    private String result;

    private PrintStream log;
    String resp = null;

    public RunscopeTrigger(PrintStream logger, String url, String accessToken) {
        this.log = logger;
        this.url = url;
        this.accessToken = accessToken;
    }

    @Override
    public String call() throws Exception {

        String resultsUrl = process(url, TEST_TRIGGER);
        log.println("Test Results URL:" + resultsUrl);

        /* TODO: If bucketId or test run detail URI gets added to trigger 
           response, use those instead of regex */
        String apiResultsUrl = resultsUrl.replaceAll(RUNSCOPE_HOST + "\\/radar\\/([^\\/]+)\\/([^\\/]+)\\/results\\/([^\\/]+)", API_HOST + "/buckets/$1/radar/$2/results/$3");
        log.println("API URL:" + apiResultsUrl);

        try {
            TimeUnit.SECONDS.sleep(10);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            ex.printStackTrace();
        }

        while (true) {
            resp = process(apiResultsUrl, TEST_RESULTS);
            log.println("Response received:" + resp);

            /* If test run is not complete, sleep 1s and try again. */
            if (TEST_RESULTS_WORKING.equalsIgnoreCase(resp) || TEST_RESULTS_QUEUED.equalsIgnoreCase(resp)) {
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            } else {
                break;
            }
        }
        return resp;
    }

    /**
     * Method for making HTTP call
     *
     * @param url
     * @param apiEndPoint
     * @return
     */
    public String process(String url, final String apiEndPoint) {

        final CloseableHttpAsyncClient httpclient = HttpAsyncClients.createDefault();
        try {
            httpclient.start();

            final RequestConfig config = RequestConfig.custom()
                    .setConnectTimeout(60 * 1000)
                    .setConnectionRequestTimeout(60 * 1000)
                    .setSocketTimeout(60 * 1000).build();

            final HttpGet request = new HttpGet(url);
            request.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
            request.setHeader("User-Agent", "runscope-jenkins-plugin/1.44-SNAPSHOT");
            request.setConfig(config);
            final Future<HttpResponse> future = httpclient.execute(request, null);
            final HttpResponse response = future.get();
            String responseBody = EntityUtils.toString(response.getEntity(), "UTF-8");
            log.println("Data received: " + responseBody);
            result = parseJSON(responseBody, apiEndPoint);
            httpclient.close();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Exception: ", e);
            e.printStackTrace();
        } finally {
            try {
                httpclient.close();
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Error closing connection: ", e);
                e.printStackTrace();
            }
        }
        return result;
    }


    /**
     * @param data
     * @param apiEndPoint
     * @return test result
     */
    private String parseJSON(String data, String apiEndPoint) {

        log.println("API EndPoint: " + apiEndPoint);

        JSONObject jsonObject = JSONObject.fromObject(data);
        JSONObject dataObject = (JSONObject) jsonObject.get("data");

        if (TEST_TRIGGER.equals(apiEndPoint)) {
            JSONArray runsArray = dataObject.getJSONArray("runs");
            JSONObject runsObject = (JSONObject) runsArray.get(0);
            return runsObject.get("url").toString();
        }

        String testResult = dataObject.get("result").toString();

        if (TEST_RESULTS_PASS.equals(testResult)) {
            log.println("Test run passed successfully");
        } else if (TEST_RESULTS_FAIL.equals(testResult)) {
            log.println("Test run failed, marking the build as failed");
            LOGGER.log(Level.SEVERE, "Test run failed");
        }
        return testResult;
    }

    private static final Logger LOGGER = Logger.getLogger(RunscopeTrigger.class.getName());
}
