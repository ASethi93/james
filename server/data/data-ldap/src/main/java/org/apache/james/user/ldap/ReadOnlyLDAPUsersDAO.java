/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.user.ldap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import javax.inject.Inject;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.commons.lang3.StringUtils;
import org.apache.directory.api.ldap.model.filter.FilterEncoder;
import org.apache.james.core.Username;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.api.model.User;
import org.apache.james.user.ldap.api.LdapConstants;
import org.apache.james.user.ldap.retry.DoublingRetrySchedule;
import org.apache.james.user.ldap.retry.api.RetrySchedule;
import org.apache.james.user.ldap.retry.naming.ldap.RetryingLdapContext;
import org.apache.james.user.lib.UsersDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.base.Strings;

public class ReadOnlyLDAPUsersDAO implements UsersDAO, Configurable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReadOnlyLDAPUsersDAO.class);

    // The name of the factory class which creates the initial context
    // for the LDAP service provider
    private static final String INITIAL_CONTEXT_FACTORY = "com.sun.jndi.ldap.LdapCtxFactory";

    private static final String PROPERTY_NAME_CONNECTION_POOL = "com.sun.jndi.ldap.connect.pool";
    private static final String PROPERTY_NAME_CONNECT_TIMEOUT = "com.sun.jndi.ldap.connect.timeout";
    private static final String PROPERTY_NAME_READ_TIMEOUT = "com.sun.jndi.ldap.read.timeout";

    /**
     * The context for the LDAP server. This is the connection that is built
     * from the configuration attributes &quot;ldapHost&quot;,
     * &quot;principal&quot; and &quot;credentials&quot;.
     */
    private LdapContext ldapContext;
    // The schedule for retry attempts
    private RetrySchedule schedule = null;

    private LdapRepositoryConfiguration ldapConfiguration;

    @Inject
    public ReadOnlyLDAPUsersDAO() {

    }

    /**
     * Extracts the parameters required by the repository instance from the
     * James server configuration data. The fields extracted include
     * {@link LdapRepositoryConfiguration#ldapHost}, {@link LdapRepositoryConfiguration#userIdAttribute}, {@link LdapRepositoryConfiguration#userBase},
     * {@link LdapRepositoryConfiguration#principal}, {@link LdapRepositoryConfiguration#credentials} and {@link LdapRepositoryConfiguration#restriction}.
     *
     * @param configuration
     *            An encapsulation of the James server configuration data.
     */
    @Override
    public void configure(HierarchicalConfiguration<ImmutableNode> configuration) throws ConfigurationException {
        configure(LdapRepositoryConfiguration.from(configuration));
    }

    public void configure(LdapRepositoryConfiguration configuration) {
        ldapConfiguration = configuration;

        schedule = new DoublingRetrySchedule(
            configuration.getRetryStartInterval(),
            configuration.getRetryMaxInterval(),
            configuration.getScale());
    }

    /**
     * Initialises the user-repository instance. It will create a connection to
     * the LDAP host using the supplied configuration.
     *
     * @throws Exception
     *             If an error occurs authenticating or connecting to the
     *             specified LDAP host.
     */
    public void init() throws Exception {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(this.getClass().getName() + ".init()" + '\n' + "LDAP host: " + ldapConfiguration.getLdapHost()
                + '\n' + "User baseDN: " + ldapConfiguration.getUserBase() + '\n' + "userIdAttribute: "
                + ldapConfiguration.getUserIdAttribute() + '\n' + "Group restriction: " + ldapConfiguration.getRestriction()
                + '\n' + "UseConnectionPool: " + ldapConfiguration.useConnectionPool() + '\n' + "connectionTimeout: "
                + ldapConfiguration.getConnectionTimeout() + '\n' + "readTimeout: " + ldapConfiguration.getReadTimeout()
                + '\n' + "retrySchedule: " + schedule + '\n' + "maxRetries: " + ldapConfiguration.getMaxRetries() + '\n');
        }
        // Setup the initial LDAP context
        updateLdapContext();
    }

    protected void updateLdapContext() throws NamingException {
        ldapContext = computeLdapContext();
    }

    /**
     * Answers a new LDAP/JNDI context using the specified user credentials.
     *
     * @return an LDAP directory context
     * @throws NamingException
     *             Propagated from underlying LDAP communication API.
     */
    protected LdapContext computeLdapContext() throws NamingException {
        return new RetryingLdapContext(schedule, ldapConfiguration.getMaxRetries()) {

            @Override
            public Context newDelegate() throws NamingException {
                return new InitialLdapContext(getContextEnvironment(), null);
            }
        };
    }

    protected Properties getContextEnvironment() {
        Properties props = new Properties();
        props.put(Context.INITIAL_CONTEXT_FACTORY, INITIAL_CONTEXT_FACTORY);
        props.put(Context.PROVIDER_URL, Optional.ofNullable(ldapConfiguration.getLdapHost())
            .orElse(""));
        if (Strings.isNullOrEmpty(ldapConfiguration.getCredentials())) {
            props.put(Context.SECURITY_AUTHENTICATION, LdapConstants.SECURITY_AUTHENTICATION_NONE);
        } else {
            props.put(Context.SECURITY_AUTHENTICATION, LdapConstants.SECURITY_AUTHENTICATION_SIMPLE);
            props.put(Context.SECURITY_PRINCIPAL, Optional.ofNullable(ldapConfiguration.getPrincipal())
                .orElse(""));
            props.put(Context.SECURITY_CREDENTIALS, ldapConfiguration.getCredentials());
        }
        // The following properties are specific to com.sun.jndi.ldap.LdapCtxFactory
        props.put(PROPERTY_NAME_CONNECTION_POOL, String.valueOf(ldapConfiguration.useConnectionPool()));
        if (ldapConfiguration.getConnectionTimeout() > -1) {
            props.put(PROPERTY_NAME_CONNECT_TIMEOUT, String.valueOf(ldapConfiguration.getConnectionTimeout()));
        }
        if (ldapConfiguration.getReadTimeout() > -1) {
            props.put(PROPERTY_NAME_READ_TIMEOUT, Integer.toString(ldapConfiguration.getReadTimeout()));
        }
        return props;
    }

    /**
     * Indicates if the user with the specified DN can be found in the group
     * membership map&#45;as encapsulated by the specified parameter map.
     *
     * @param userDN
     *            The DN of the user to search for.
     * @param groupMembershipList
     *            A map containing the entire group membership lists for the
     *            configured groups. This is organised as a map of
     *
     *            <code>&quot;&lt;groupDN&gt;=&lt;[userDN1,userDN2,...,userDNn]&gt;&quot;</code>
     *            pairs. In essence, each <code>groupDN</code> string is
     *            associated to a list of <code>userDNs</code>.
     * @return <code>True</code> if the specified userDN is associated with at
     *         least one group in the parameter map, and <code>False</code>
     *         otherwise.
     */
    private boolean userInGroupsMembershipList(String userDN,
            Map<String, Collection<String>> groupMembershipList) {
        boolean result = false;

        Collection<Collection<String>> memberLists = groupMembershipList.values();
        Iterator<Collection<String>> memberListsIterator = memberLists.iterator();

        while (memberListsIterator.hasNext() && !result) {
            Collection<String> groupMembers = memberListsIterator.next();
            result = groupMembers.contains(userDN);
        }

        return result;
    }

    /**
     * Gets all the user entities taken from the LDAP server, as taken from the
     * search-context given by the value of the attribute {@link LdapRepositoryConfiguration#userBase}.
     *
     * @return A set containing all the relevant users found in the LDAP
     *         directory.
     * @throws NamingException
     *             Propagated from the LDAP communication layer.
     */
    private Set<String> getAllUsersFromLDAP() throws NamingException {
        Set<String> result = new HashSet<>();

        SearchControls sc = new SearchControls();
        sc.setSearchScope(SearchControls.SUBTREE_SCOPE);
        sc.setReturningAttributes(new String[] { "distinguishedName" });
        NamingEnumeration<SearchResult> sr = ldapContext.search(ldapConfiguration.getUserBase(), "(objectClass="
                + ldapConfiguration.getUserObjectClass() + ")", sc);
        while (sr.hasMore()) {
            SearchResult r = sr.next();
            result.add(r.getNameInNamespace());
        }

        return result;
    }

    /**
     * Extract the user attributes for the given collection of userDNs, and
     * encapsulates the user list as a collection of {@link ReadOnlyLDAPUser}s.
     * This method delegates the extraction of a single user's details to the
     * method {@link #buildUser(String)}.
     *
     * @param userDNs
     *            The distinguished-names (DNs) of the users whose information
     *            is to be extracted from the LDAP repository.
     * @return A collection of {@link ReadOnlyLDAPUser}s as taken from the LDAP
     *         server.
     * @throws NamingException
     *             Propagated from the underlying LDAP communication layer.
     */
    private Collection<ReadOnlyLDAPUser> buildUserCollection(Collection<String> userDNs)
            throws NamingException {
        List<ReadOnlyLDAPUser> results = new ArrayList<>();

        for (String userDN : userDNs) {
            Optional<ReadOnlyLDAPUser> user = buildUser(userDN);
            user.ifPresent(results::add);
        }

        return results;
    }

    /**
     * For a given name, this method makes ldap search in userBase with filter {@link LdapRepositoryConfiguration#userIdAttribute}=name
     * and objectClass={@link LdapRepositoryConfiguration#userObjectClass} and builds {@link User} based on search result.
     *
     * @param name
     *            The userId which should be value of the field {@link LdapRepositoryConfiguration#userIdAttribute}
     * @return A {@link ReadOnlyLDAPUser} instance which is initialized with the
     *         userId of this user and ldap connection information with which
     *         the user was searched. Return null if such a user was not found.
     * @throws NamingException
     *             Propagated by the underlying LDAP communication layer.
     */
    private ReadOnlyLDAPUser searchAndBuildUser(Username name) throws NamingException {
        SearchControls sc = new SearchControls();
        sc.setSearchScope(SearchControls.SUBTREE_SCOPE);
        sc.setReturningAttributes(new String[] { ldapConfiguration.getUserIdAttribute() });
        sc.setCountLimit(1);

        String filterTemplate = "(&({0}={1})(objectClass={2})" +
            StringUtils.defaultString(ldapConfiguration.getFilter(), "") +
            ")";

        String sanitizedFilter = FilterEncoder.format(
            filterTemplate,
            ldapConfiguration.getUserIdAttribute(),
            name.asString(),
            ldapConfiguration.getUserObjectClass());

        NamingEnumeration<SearchResult> sr = ldapContext.search(ldapConfiguration.getUserBase(), sanitizedFilter, sc);

        if (!sr.hasMore()) {
            return null;
        }

        SearchResult r = sr.next();
        Attribute userName = r.getAttributes().get(ldapConfiguration.getUserIdAttribute());

        if (!ldapConfiguration.getRestriction().isActivated()
            || userInGroupsMembershipList(r.getNameInNamespace(), ldapConfiguration.getRestriction().getGroupMembershipLists(ldapContext))) {
            return new ReadOnlyLDAPUser(Username.of(userName.get().toString()), r.getNameInNamespace(), ldapContext);
        }

        return null;
    }

    /**
     * Given a userDN, this method retrieves the user attributes from the LDAP
     * server, so as to extract the items that are of interest to James.
     * Specifically it extracts the userId, which is extracted from the LDAP
     * attribute whose name is given by the value of the field
     * {@link LdapRepositoryConfiguration#userIdAttribute}.
     *
     * @param userDN
     *            The distinguished-name of the user whose details are to be
     *            extracted from the LDAP repository.
     * @return A {@link ReadOnlyLDAPUser} instance which is initialized with the
     *         userId of this user and ldap connection information with which
     *         the userDN and attributes were obtained.
     * @throws NamingException
     *             Propagated by the underlying LDAP communication layer.
     */
    private Optional<ReadOnlyLDAPUser> buildUser(String userDN) throws NamingException {
      Attributes userAttributes = ldapContext.getAttributes(userDN);
      Optional<Attribute> userName = Optional.ofNullable(userAttributes.get(ldapConfiguration.getUserIdAttribute()));
      return userName
          .map(Throwing.<Attribute, String>function(u -> u.get().toString()).sneakyThrow())
          .map(Username::of)
          .map(username -> new ReadOnlyLDAPUser(username, userDN, ldapContext));
    }

    @Override
    public boolean contains(Username name) throws UsersRepositoryException {
        return getUserByName(name).isPresent();
    }

    @Override
    public int countUsers() throws UsersRepositoryException {
        try {
            return Math.toIntExact(getValidUsers().stream()
                .map(Throwing.function(this::buildUser).sneakyThrow())
                .flatMap(Optional::stream)
                .count());
        } catch (NamingException e) {
            LOGGER.error("Unable to retrieve user count from ldap", e);
            throw new UsersRepositoryException("Unable to retrieve user count from ldap", e);

        }
    }

    @Override
    public Optional<User> getUserByName(Username name) throws UsersRepositoryException {
        try {
          return Optional.ofNullable(searchAndBuildUser(name));
        } catch (NamingException e) {
            LOGGER.error("Unable to retrieve user from ldap", e);
            throw new UsersRepositoryException("Unable to retrieve user from ldap", e);

        }
    }

    @Override
    public Iterator<Username> list() throws UsersRepositoryException {
        try {
            return buildUserCollection(getValidUsers())
                .stream()
                .map(ReadOnlyLDAPUser::getUserName)
                .collect(Guavate.toImmutableList())
                .iterator();
        } catch (NamingException namingException) {
            throw new UsersRepositoryException(
                    "Unable to retrieve users list from LDAP due to unknown naming error.",
                    namingException);
        }
    }

    private Collection<String> getValidUsers() throws NamingException {
        Set<String> userDNs = getAllUsersFromLDAP();
        Collection<String> validUserDNs;

        if (ldapConfiguration.getRestriction().isActivated()) {
            Map<String, Collection<String>> groupMembershipList = ldapConfiguration.getRestriction()
                    .getGroupMembershipLists(ldapContext);
            validUserDNs = new ArrayList<>();

            Iterator<String> userDNIterator = userDNs.iterator();
            String userDN;
            while (userDNIterator.hasNext()) {
                userDN = userDNIterator.next();
                if (userInGroupsMembershipList(userDN, groupMembershipList)) {
                    validUserDNs.add(userDN);
                }
            }
        } else {
            validUserDNs = userDNs;
        }
        return validUserDNs;
    }

    @Override
    public void removeUser(Username name) throws UsersRepositoryException {
        throw new UsersRepositoryException("This user-repository is read-only. Modifications are not permitted.");

    }

    @Override
    public void updateUser(User user) throws UsersRepositoryException {
        throw new UsersRepositoryException("This user-repository is read-only. Modifications are not permitted.");
    }

    @Override
    public void addUser(Username username, String password) throws UsersRepositoryException {
        throw new UsersRepositoryException("This user-repository is read-only. Modifications are not permitted.");
    }
}
