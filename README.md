Runscope API Test Plugin for Jenkins
-------------
With this plugin you can add a build step to your Jenkins project that executes a Runscope API test. You would add this step after your API has been deployed and is ready for testing. After the Runscope API is triggered, the plugin will wait for the test run to finish (or timeout). If the API test step is successful, your pipeline will continue to the next step in your pipeline; however, if it fails (or times out), the build will be marked as failed.

## Installing
1. Download and unpack the source
2. From terminal ```mvn clean install```
3. Copy ```target/Runscope.hpi``` to ```$JENKINS_HOME/plugins```
4. Restart Jenkins 

## Prerequisites
* **Trigger URL** - every Runscope API test has it's a unique URL that allows you to execute a test run from remote. Fetch the Trigger URL from your Runscope test's settings. (i.e. ```https://api.runscope.com/radar/abcdefg/trigger```)
* **Access Token** - in order for the plugin to check on the status of triggered test (via the Runscope API), it requires an OAuth access token. To create an access token, login to your Runscope account and navigate to [https://www.runscope.com/applications](https://www.runscope.com/applications). Here, click ```Create Application``` -- for name, type in *Jenkins Plugin*. For website and callback URLs, you can just type in ```https://www.runscope.com``` as placeholders (these URLs are never used -- we're just creating an application for the personal access token). After you've created the application, copy down the personal access token (i.e. ```5ffffffe-ab99-43ff-7777-3333deee99f9```)
 
## Usage
 1. Open your project configuration from the Jenkins dashboard. 
 2. In the build section, click ```Add build step``` and select ```Runscope Test Congiruation```. 
 3. A form will appear. For the ```Test Trigger URL``` field, paste in the Trigger URL from above. For the ```Access Token``` field, paste in the personal access token from above. 
 4. Save your Jenkins project.

## ToDo List
* Add this project to the official Jenkins plugin repository


