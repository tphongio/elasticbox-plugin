/*
 * ElasticBox Confidential
 * Copyright (c) 2014 All Right Reserved, ElasticBox Inc.
 *
 * NOTICE:  All information contained herein is, and remains the property
 * of ElasticBox. The intellectual and technical concepts contained herein are
 * proprietary and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law. Dissemination of this
 * information or reproduction of this material is strictly forbidden unless prior
 * written permission is obtained from ElasticBox.
 */

package com.elasticbox.jenkins.tests;

import com.elasticbox.Client;
import com.elasticbox.ClientException;
import com.elasticbox.IProgressMonitor;
import com.elasticbox.jenkins.ElasticBoxCloud;
import com.elasticbox.jenkins.util.SlaveInstance;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import java.text.MessageFormat;
import java.util.Arrays;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.PostMethod;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 *
 * @author Phong Nguyen Le
 */
public class ElasticBoxCloudTest extends TestBase {
    private JSONObject provider;
    private TestBoxData testJenkinsSlaveBoxData;
    
    @Before    
    public void setupTestData() throws Exception {
        Client client = cloud.getClient();
        testJenkinsSlaveBoxData = TestUtils.createTestBox("boxes/linux-jenkins-slave/linux-jenkins-slave.json", null, client);
        provider = TestUtils.createTestProvider(client);
    }
    
    @After
    public void cleanUp() throws Exception {
        Client client = cloud.getClient();
        client.doDelete(testJenkinsSlaveBoxData.getJson().getString("uri"));
        client.doDelete(provider.getString("uri"));
    }
        
    @Test
    public void testClient() throws Exception {
        Client client = cloud.getClient();
        TestUtils.createTestProfile(testJenkinsSlaveBoxData, provider, null, client);
        JSONObject testJenkinsSlaveBox = testJenkinsSlaveBoxData.getJson();
        JSONArray profiles = client.getProfiles(TestUtils.TEST_WORKSPACE, testJenkinsSlaveBox.getString("id"));
        Assert.assertTrue(MessageFormat.format("Box {0} does not have any profile in workspace {1}",
                testJenkinsSlaveBox.getString("uri"), TestUtils.TEST_WORKSPACE), profiles.size() > 0);
        for (Object profile : profiles) {
            JSONObject profileJson = client.getProfile(((JSONObject) profile).getString("id"));
            Assert.assertEquals(profile.toString(), profileJson.toString());
        }        
        
        // make sure that a deployment request can be successfully submitted
        JSONObject profile  = TestUtils.createTestProfile(testJenkinsSlaveBoxData, provider, null, client);
        String slaveName = testJenkinsSlaveBox.getString("name").replace(' ', '-').toLowerCase();
        JSONArray variables = SlaveInstance.createJenkinsVariables(jenkins.getInstance().getRootUrl(), slaveName);
        JSONObject variable = new JSONObject();
        variable.put("name", "JNLP_SLAVE_OPTIONS");
        variable.put("type", "Text");
        variable.put("value", MessageFormat.format("-jnlpUrl {0}/computer/{1}/slave-agent.jnlp", slaveName));
        variables.add(variable);                        
        IProgressMonitor monitor = client.deploy(profile.getString("id"), profile.getString("owner"), 
                "jenkins-plugin-unit-test", 1, variables);
        try {
            monitor.waitForDone(60);
        } catch (IProgressMonitor.IncompleteException ex) {
            
        }
        
        String instanceId = Client.getResourceId(monitor.getResourceUrl());
        monitor = client.terminate(instanceId);
        monitor.waitForDone(60);
        client.delete(instanceId);
        try {
            client.getInstance(instanceId);
            throw new Exception(MessageFormat.format("Instance {0} was not deleted", instanceId));
        } catch (ClientException ex) {
            Assert.assertEquals(HttpStatus.SC_NOT_FOUND, ex.getStatusCode());
        }
    }
    
    public void testConfigRoundtrip() throws Exception {
        JenkinsRule.WebClient webClient = jenkins.createWebClient();
        HtmlForm configForm = webClient.goTo("configure").getFormByName("config");
        jenkins.submit(webClient.goTo("configure").getFormByName("config")); 
//        assertEqualBeans(cloud, jenkins.getInstance().clouds.iterator().next(), "endpointUrl,maxInstances,retentionTime,username,password");
        
        configForm.submit(configForm.getButtonByCaption("Test Connection"));
        
        // test connection
        PostMethod post = new PostMethod(MessageFormat.format("{0}descriptorByName/{1}/testConnection?.crumb=test", jenkins.getInstance().getRootUrl(), ElasticBoxCloud.class.getName()));        
        post.setRequestBody(Arrays.asList(new NameValuePair("endpointUrl", cloud.getEndpointUrl()),
                new NameValuePair("username", cloud.getUsername()),
                new NameValuePair("password", cloud.getPassword())).toArray(new NameValuePair[0]));
        HttpClient httpClient = new HttpClient();
        int status = httpClient.executeMethod(post);
        String content = post.getResponseBodyAsString();
        Assert.assertEquals(HttpStatus.SC_OK, status);
        Assert.assertTrue(content, content.contains(MessageFormat.format("Connection to {0} was successful.", cloud.getEndpointUrl())));
    }
    
}
