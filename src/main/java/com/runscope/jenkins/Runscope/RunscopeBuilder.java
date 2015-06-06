package com.runscope.jenkins.Runscope;

import java.io.PrintStream;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import hudson.Launcher;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * RunscopeBuilder {@link Builder}.
 *
 * @author Harmeek Jhutty
 * @email  hjhutty@redeploy.io
 */
public class RunscopeBuilder extends Publisher {

    private static final String DISPLAY_NAME = "Runscope Test Configuration";
    private static final String TEST_RESULTS_PASS = "pass";
 	
    private final String triggerEndPoint;
    private final String accessToken;
    private int timeout = 60;
    
    public String resp;

    @DataBoundConstructor
    public RunscopeBuilder(String triggerEndPoint, String accessToken, int timeout) {
		this.triggerEndPoint = triggerEndPoint;
		this.accessToken = accessToken;
		if(timeout >= 0 )
		    this.timeout = timeout;
	}

    /**
     * @return the triggerEndPoint
     */
    public String getTriggerEndPoint() {
	return triggerEndPoint;
    }
	
    /**
     * @return the accessToken
     */
    public String getAccessToken() {
	return accessToken;
    }
	
    /**
     * @return the timeout
     */
    public Integer getTimeout() {
	return timeout;
    }
    
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    /* 
     * @see hudson.tasks.BuildStepCompatibilityLayer#perform(hudson.model.AbstractBuild, hudson.Launcher, hudson.model.BuildListener)
     */
    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
    	
    	PrintStream logger = listener.getLogger();

    	logger.println("Build Trigger Configuration:");
    	logger.println("Trigger End Point:" + triggerEndPoint);
    	logger.println("Access Token:" + accessToken);
    	logger.println("Timeout:" + timeout);
    	
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<String> future = executorService.submit(new RunscopeTrigger(logger, triggerEndPoint, accessToken /*triggerEndPoint, */));

        try {
            String result = future.get(timeout, TimeUnit.SECONDS);
            if (!TEST_RESULTS_PASS.equalsIgnoreCase(result)) {
        	build.setResult(Result.FAILURE);
        	return false;
            }
        } catch (TimeoutException e) {
            logger.println("Timeout Exception:" + e.getMessage());
            build.setResult(Result.FAILURE);
            return false;
        } catch (Exception e) {
            logger.println("Exception:" + e.getMessage());
            build.setResult(Result.FAILURE);
            return false;
        }
        executorService.shutdownNow();
        
        return true;
    }

    /**
     * Descriptor for {@link RunscopeBuilder}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     */
    @Extension 
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        /**
         * In order to load the persisted global configuration, you have to 
         * call load() in the constructor.
         */
        public DescriptorImpl() {
            super(RunscopeBuilder.class);
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        /**
         * This name is used in the configuration screen.
         */
        public String getDisplayName() {
            return DISPLAY_NAME;
        }

    }

}

