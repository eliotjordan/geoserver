/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.web.security.ldap;

import java.util.HashMap;
import java.util.Map;

import org.apache.wicket.Component;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.StringResourceModel;
import org.apache.wicket.model.util.MapModel;
import org.geoserver.data.test.SystemTestData;
import org.geoserver.security.config.SecurityManagerConfig;
import org.geoserver.security.ldap.LDAPSecurityServiceConfig;
import org.geoserver.security.ldap.LDAPTestUtils;
import org.geoserver.security.web.AbstractSecurityWicketTestSupport;
import org.geoserver.web.ComponentBuilder;
import org.geoserver.web.FormTestPage;
import org.junit.After;
import org.junit.Assume;
import org.junit.Test;
import org.springframework.ldap.test.LdapTestUtils;

/**
 * 
 * @author "Mauro Bartolomeoli - mauro.bartolomeoli@geo-solutions.it"
 * 
 */
public class LDAPAuthProviderPanelTest extends AbstractSecurityWicketTestSupport {
    
    private static final String USER_FORMAT = "uid={0},ou=People,dc=example,dc=com";

    private static final String USER_FILTER = "(telephonenumber=1)";

    private static final String USER_DN_PATTERN = "uid={0},ou=People";

    LDAPAuthProviderPanel current;
    
    String relBase = "panel:";
    String base = "form:" + relBase;
    
    LDAPSecurityServiceConfig config;
    
    FeedbackPanel feedbackPanel = null;
    
    private static final String ldapServerUrl = LDAPTestUtils.LDAP_SERVER_URL;
    private static final String basePath = LDAPTestUtils.LDAP_BASE_PATH;
    
    
    @After
    public void tearDown() throws Exception {
        LdapTestUtils
                .destroyApacheDirectoryServer(LdapTestUtils.DEFAULT_PRINCIPAL,
                        LdapTestUtils.DEFAULT_PASSWORD);
    }
    
    
	protected void setupPanel(final String userDnPattern, String userFilter,
			String userFormat, String userGroupService) {
        config = new LDAPSecurityServiceConfig();
        config.setName("test");
        config.setServerURL(ldapServerUrl + "/" + basePath);
        config.setUserDnPattern(userDnPattern);
        config.setUserFilter(userFilter);
        config.setUserFormat(userFormat);
        config.setUserGroupServiceName(userGroupService);
        setupPanel(config);
    }
    
    @Override
    protected void onSetUp(SystemTestData testData) throws Exception {
        super.onSetUp(testData);
        // disable url parameter encoding for these tests
        SecurityManagerConfig config = getSecurityManager().getSecurityConfig();
        config.setEncryptingUrlParams(false);
        getSecurityManager().saveSecurityConfig(config);
    }
    
    protected void setupPanel(LDAPSecurityServiceConfig theConfig) {
        this.config = theConfig;
        tester.startPage(new FormTestPage(new ComponentBuilder() {
            private static final long serialVersionUID = 1L;
    
            public Component buildComponent(String id) {
                
                return current = new LDAPAuthProviderPanel(id, new Model(config));
            };
        }, new CompoundPropertyModel(config)){

            @Override
            protected void onBeforeRender() {
                feedbackPanel = new FeedbackPanel("feedback");
                feedbackPanel.setOutputMarkupId(true);
                add(feedbackPanel);
                super.onBeforeRender();
            }
            
        });
    }
    
    @Test
    public void testTestConnectionWithDnLookup() throws Exception {
        Assume.assumeTrue(LDAPTestUtils.initLdapServer(true, ldapServerUrl, basePath));
        setupPanel(USER_DN_PATTERN, null, null, null);
        testSuccessfulConnection();
    }
    
    @Test
    public void testTestConnectionWitUserGroupService() throws Exception {
        Assume.assumeTrue(LDAPTestUtils.initLdapServer(true, ldapServerUrl, basePath));
        setupPanel(USER_DN_PATTERN, null, null, "default");
        testSuccessfulConnection();
    }
    
    @Test
    public void testTestConnectionWithUserFilter() throws Exception {
        Assume.assumeTrue(LDAPTestUtils.initLdapServer(true, ldapServerUrl, basePath));
        setupPanel(null, USER_FILTER, USER_FORMAT, null);
        testSuccessfulConnection();
    }


    
    
    @Test
    public void testTestConnectionFailedWithDnLookup() throws Exception {
        Assume.assumeTrue(LDAPTestUtils.initLdapServer(true, ldapServerUrl, basePath));
        setupPanel(USER_DN_PATTERN, null, null, null);
        testFailedConnection();
    }
    
    @Test
    public void testTestConnectionFailedWithUserFilter() throws Exception {
        Assume.assumeTrue(LDAPTestUtils.initLdapServer(true, ldapServerUrl, basePath));
        setupPanel(null, USER_FILTER, USER_FORMAT, null);
        testFailedConnection();
    }

    private void testSuccessfulConnection() throws Exception {
        authenticate("admin", "admin");

        tester.assertNoErrorMessage();
        String success = new StringResourceModel(LDAPAuthProviderPanel.class.getSimpleName() + 
                ".connectionSuccessful", null).getObject();
        tester.assertInfoMessages(new String[] { success });
    }


    
    
    private void testFailedConnection() throws Exception {
        authenticate("admin", "wrong");
            
        tester.assertNoInfoMessage();
        tester.assertContains("AuthenticationException");
    }
    
    private void authenticate(String username, String password) {
        TextField userField = ((TextField)tester.getComponentFromLastRenderedPage(base+ "testCx:username"));
        userField.setDefaultModel(new Model(username));
        TextField passwordField = ((TextField)tester.getComponentFromLastRenderedPage(base+ "testCx:password"));
        passwordField.setDefaultModel(new Model(password));
        
        Map<String, String> map = new HashMap<String, String>();
        map.put("username", username);
        map.put("password", password);
        
        tester.getComponentFromLastRenderedPage("form:panel:testCx").setDefaultModel(new MapModel<String, String>(map));
        
        tester.clickLink(base+ "testCx:test", true);
    }
}
