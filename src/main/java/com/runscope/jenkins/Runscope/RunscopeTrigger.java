package com.runscope.jenkins.Runscope;

import java.io.IOException;
import java.io.PrintStream;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.net.Proxy;

import hudson.ProxyConfiguration;
import jenkins.model.Jenkins;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
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
 * email help@runscope.com
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

    private PrintStream log;
    String resp = null;

    public RunscopeTrigger(PrintStream logger, String url, String accessToken) {
        this.log = logger;
        this.url = url;
        this.accessToken = accessToken;
    }

    @Override
    public String call() throws Exception {

        String urlsJson = process(url, TEST_TRIGGER);

        //Fail fast if trigger request fails
        if (urlsJson.isEmpty()) {
            return "";
        }

        JSONArray urlsArray = JSONArray.fromObject(urlsJson);
        List<String> resultsUrls = new ArrayList<>();

        //Build collection of test result urls
        log.println("Test Results URLs:");
        for (int i = 0; i < urlsArray.size(); i++) {
            JSONObject runObject = urlsArray.getJSONObject(i);
            log.println(runObject.getString("url"));
            resultsUrls.add(runObject.getString("url"));
        }

        /* TODO: If bucketId or test run detail URI gets added to trigger
           response, use those instead of regex */

        //Convert each test result url to an api result url
        log.println("API URLs:");
        ListIterator<String> apiIterator = resultsUrls.listIterator();
        while (apiIterator.hasNext()) {
            String apiResultsUrl = apiIterator.next().replaceAll(RUNSCOPE_HOST + "\\/radar\\/([^\\/]+)\\/([^\\/]+)\\/results\\/([^\\/]+)", API_HOST + "/buckets/$1/radar/$2/results/$3");
            log.println(apiResultsUrl);
            apiIterator.set(apiResultsUrl);
        }


        try {
            TimeUnit.SECONDS.sleep(10);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            ex.printStackTrace();
        }

        //Poll each test sequentially until pass or fail
        //This ensures that the job finishes only when all tests have finished
        ListIterator<String> resultsIterator = resultsUrls.listIterator();
        String finalResult = TEST_RESULTS_PASS;
        while (resultsIterator.hasNext()) {
            String testResultsUrl = resultsIterator.next();
            log.println("Polling Test: " + testResultsUrl);

            while (true) {
                resp = process(testResultsUrl, TEST_RESULTS);
                log.println("Response received:" + resp);

                /* If test run is not complete, sleep 1s and try again. */
                if (TEST_RESULTS_WORKING.equalsIgnoreCase(resp) || TEST_RESULTS_QUEUED.equalsIgnoreCase(resp)) {
                    try {
                        TimeUnit.SECONDS.sleep(1);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    if (TEST_RESULTS_FAIL.equalsIgnoreCase(resp)) {
                      log.println("Test FAILED!");
                      finalResult = TEST_RESULTS_FAIL;
                    }
                    break;
                }
            }
        }
        return finalResult;
    }

    /**
     * Method for making HTTP call
     *
     * @param url
     * @param apiEndPoint
     * @return
     */
    public String process(String url, final String apiEndPoint) {
        String result = "";
        final CloseableHttpAsyncClient httpclient = HttpAsyncClients.createDefault();
        try {
            httpclient.start();

	    Jenkins j = Jenkins.getInstance();
	    ProxyConfiguration proxyConfig = null;
	    if (j != null) {
		    proxyConfig = j.proxy;
	    }

	    RequestConfig config = null;

	    if (proxyConfig != null) {
		    HttpHost proxy = new HttpHost(proxyConfig.name, proxyConfig.port);
		    config = RequestConfig.custom()
			    .setConnectTimeout(60 * 1000)
			    .setConnectionRequestTimeout(60 * 1000)
			    .setSocketTimeout(60 * 1000)
			    .setProxy(proxy)
			    .build();
	    } else {
		    config = RequestConfig.custom()
			    .setConnectTimeout(60 * 1000)
			    .setConnectionRequestTimeout(60 * 1000)
			    .setSocketTimeout(60 * 1000)
			    .build();
	    }

            final HttpGet request = new HttpGet(url);
            request.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
            request.setHeader("User-Agent", "runscope-jenkins-plugin/1.47");
            request.setConfig(config);
            final Future<HttpResponse> future = httpclient.execute(request, null);
            final HttpResponse response = future.get();

            final int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200 && statusCode != 201) {
              log.println(String.format("Error retrieving details from Runscope API, marking as failed: %s", statusCode));
              return result;
            } else {
              String responseBody = EntityUtils.toString(response.getEntity(), "UTF-8");
              result = parseJSON(responseBody, apiEndPoint);
            }
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
            String runsJson = dataObject.getString("runs");

            return runsJson;
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
