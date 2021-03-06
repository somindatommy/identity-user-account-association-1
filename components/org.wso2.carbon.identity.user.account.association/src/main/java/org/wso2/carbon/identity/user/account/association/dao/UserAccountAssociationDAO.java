/*
*  Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/

package org.wso2.carbon.identity.user.account.association.dao;

import org.wso2.carbon.context.CarbonContext;
import org.wso2.carbon.identity.application.common.util.IdentityApplicationManagementUtil;
import org.wso2.carbon.identity.core.util.IdentityDatabaseUtil;
import org.wso2.carbon.identity.core.util.IdentityUtil;
import org.wso2.carbon.identity.user.account.association.dto.UserAccountAssociationDTO;
import org.wso2.carbon.identity.user.account.association.exception.UserAccountAssociationException;
import org.wso2.carbon.identity.user.account.association.exception.UserAccountAssociationServerException;
import org.wso2.carbon.identity.user.account.association.internal.IdentityAccountAssociationServiceComponent;
import org.wso2.carbon.identity.user.account.association.util.UserAccountAssociationConstants;
import org.wso2.carbon.identity.user.account.association.util.UserAccountAssociationUtil;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.user.core.service.RealmService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class UserAccountAssociationDAO {

    private UserAccountAssociationDAO() {

    }

    public static UserAccountAssociationDAO getInstance() {
        return LazyHolder.INSTANCE;
    }

    /**
     * Add new user association
     *
     * @param associationKey Association Key
     * @param domainName     Domain name of ser
     * @param tenantId       Tenant ID of user
     * @param userName       Username
     * @throws UserAccountAssociationException
     */
    public void createUserAssociation(String associationKey, String domainName, int tenantId,
                                      String userName) throws UserAccountAssociationException {

        try (Connection dbConnection = IdentityDatabaseUtil.getDBConnection();
             PreparedStatement preparedStatement = dbConnection.prepareStatement(UserAccountAssociationConstants
                     .SQLQueries.ADD_USER_ACCOUNT_ASSOCIATION)) {

            preparedStatement.setString(1, associationKey);
            preparedStatement.setInt(2, tenantId);
            preparedStatement.setString(3, domainName);
            preparedStatement.setString(4, userName);
            preparedStatement.executeUpdate();

            if (!dbConnection.getAutoCommit()) {
                dbConnection.commit();
            }
        } catch (SQLException e) {
            throw new UserAccountAssociationServerException(UserAccountAssociationConstants.ErrorMessages
                    .CONN_CREATE_DB_ERROR.getDescription(), e);
        }
    }

    /**
     * Delete account association
     *
     * @param domainName User store domain of user
     * @param tenantId   Tenant ID of user
     * @param userName   User name
     * @throws UserAccountAssociationException
     */
    public void deleteUserAssociation(String domainName, int tenantId,
                                      String userName) throws UserAccountAssociationException {

        try (Connection dbConnection = IdentityDatabaseUtil.getDBConnection();
             PreparedStatement preparedStatement = dbConnection.prepareStatement(UserAccountAssociationConstants
                     .SQLQueries.DELETE_CONNECTION)) {

            preparedStatement.setInt(1, tenantId);
            preparedStatement.setString(2, domainName);
            preparedStatement.setString(3, userName);
            preparedStatement.executeUpdate();

            if (!dbConnection.getAutoCommit()) {
                dbConnection.commit();
            }
        } catch (SQLException e) {
            throw new UserAccountAssociationServerException(UserAccountAssociationConstants.ErrorMessages
                    .CONN_DELETE_DB_ERROR.getDescription(), e);
        }
    }

    /**
     * List accounts associated with a account
     *
     * @param domainName User store domain of user
     * @param tenantId   Tenant ID of user
     * @param userName   User name
     * @return
     * @throws UserAccountAssociationException
     */
    public List<UserAccountAssociationDTO> getAssociationsOfUser(String domainName, int tenantId,
                                                                 String userName)
            throws UserAccountAssociationException {

        List<UserAccountAssociationDTO> accountAssociations = new ArrayList<>();
        RealmService realmService;
        String associationKey = getAssociationKeyOfUser(domainName, tenantId, userName);

        if (associationKey != null) {
            try (Connection dbConnection = IdentityDatabaseUtil.getDBConnection();
                 PreparedStatement preparedStatement = dbConnection.prepareStatement(UserAccountAssociationConstants
                         .SQLQueries.LIST_USER_ACCOUNT_ASSOCIATIONS)) {

                realmService = IdentityAccountAssociationServiceComponent.getRealmService();
                preparedStatement.setString(1, associationKey);

                try (ResultSet resultSet = preparedStatement.executeQuery()) {

                    while (resultSet.next()) {
                        int conUserTenantId = resultSet.getInt(1);
                        String conUserDomain = resultSet.getString(2);
                        String conUserName = resultSet.getString(3);

                        if (domainName.equals(conUserDomain) && (tenantId == conUserTenantId) && userName.equals
                                (conUserName)) {
                            continue;
                        }

                        UserAccountAssociationDTO associationDTO = new UserAccountAssociationDTO();
                        associationDTO.setUsername(conUserName);
                        associationDTO.setDomain(conUserDomain);
                        associationDTO.setTenantDomain(realmService.getTenantManager().getDomain(conUserTenantId));
                        accountAssociations.add(associationDTO);
                    }
                }
            } catch (SQLException e) {
                throw new UserAccountAssociationServerException(String.format(UserAccountAssociationConstants.ErrorMessages
                        .ERROR_WHILE_RETRIEVING_ASSOC_OF_USER.getDescription(), userName), e);
            } catch (UserStoreException e) {
                throw new UserAccountAssociationServerException(UserAccountAssociationConstants.ErrorMessages
                        .ERROR_WHILE_GETTING_TENANT_NAME.getDescription(), e);
            }
        }

        return accountAssociations;
    }

    /**
     * Retrieve association key of a user
     *
     * @param domainName User store domain of user
     * @param tenantId   Tenant ID of user
     * @param userName   User name
     * @return
     * @throws UserAccountAssociationException
     */
    public String getAssociationKeyOfUser(String domainName, int tenantId,
                                          String userName) throws UserAccountAssociationException {

        String associationKey = null;

        try (Connection dbConnection = IdentityDatabaseUtil.getDBConnection();
             PreparedStatement preparedStatement = dbConnection.prepareStatement(UserAccountAssociationConstants
                     .SQLQueries.GET_ASSOCIATION_KEY_OF_USER)) {

            preparedStatement.setInt(1, tenantId);
            preparedStatement.setString(2, domainName);
            preparedStatement.setString(3, userName);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {

                if (resultSet.next()) {
                    associationKey = resultSet.getString(1);
                }
            }
        } catch (SQLException e) {
            throw new UserAccountAssociationServerException(UserAccountAssociationConstants.ErrorMessages
                    .ERROR_WHILE_RETRIEVING_ASSOC_KEY.getDescription(), e);
        }
        return associationKey;
    }

    /**
     * Update an association key
     *
     * @param oldAssociationKey Old association key
     * @param newAssociationKey New association key
     * @throws UserAccountAssociationException
     */
    public void updateUserAssociationKey(String oldAssociationKey, String newAssociationKey) throws
            UserAccountAssociationException {

        try (Connection dbConnection = IdentityDatabaseUtil.getDBConnection();
             PreparedStatement preparedStatement = dbConnection.prepareStatement(UserAccountAssociationConstants
                     .SQLQueries.UPDATE_ASSOCIATION_KEY)) {

            preparedStatement.setString(1, newAssociationKey);
            preparedStatement.setString(2, oldAssociationKey);
            preparedStatement.executeUpdate();

            if (!dbConnection.getAutoCommit()) {
                dbConnection.commit();
            }
        } catch (SQLException e) {
            throw new UserAccountAssociationServerException(UserAccountAssociationConstants.ErrorMessages
                    .CONN_UPDATE_DB_ERROR.getDescription(), e);
        }
    }

    /**
     * Check if logged user can be associated with a given user
     *
     * @param domainName User store domain of user
     * @param tenantId   Tenant ID of user
     * @param userName   User name
     * @return
     * @throws UserAccountAssociationException
     */
    public boolean isValidUserAssociation(String domainName, int tenantId,
                                          String userName) throws UserAccountAssociationException {

        boolean valid = false;

        try (Connection dbConnection = IdentityDatabaseUtil.getDBConnection();
             PreparedStatement preparedStatement = dbConnection.prepareStatement(UserAccountAssociationConstants
                     .SQLQueries.IS_VALID_ASSOCIATION)) {

            preparedStatement.setInt(1, tenantId);
            preparedStatement.setString(2, domainName);
            preparedStatement.setString(3, userName);
            preparedStatement.setInt(4, CarbonContext.getThreadLocalCarbonContext().getTenantId());
            preparedStatement.setString(5, IdentityUtil.extractDomainFromName(CarbonContext
                    .getThreadLocalCarbonContext().getUsername()));
            preparedStatement.setString(6, UserAccountAssociationUtil.getUsernameWithoutDomain(CarbonContext
                    .getThreadLocalCarbonContext().getUsername()));

            try (ResultSet resultSet = preparedStatement.executeQuery()) {

                if (resultSet.next()) {
                    valid = resultSet.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            throw new UserAccountAssociationServerException(UserAccountAssociationConstants.ErrorMessages
                    .CHECK_ASSOCIATION_DB_ERROR.getDescription(), e);
        }

        return valid;
    }

    /**
     * Checks if two user accounts can be populated or not
     *
     * @param domainName1 user store domain of account 1
     * @param tenantId1   tenant id of account 1
     * @param userName1   username of account 1
     * @param domainName2 user store domain of account 2
     * @param tenantId2   tenant id of account 2
     * @param userName2   username of account 2
     * @return
     * @throws UserAccountAssociationException
     */
    public boolean isValidUserAssociation(String domainName1, int tenantId1, String userName1, String domainName2,
                                          int tenantId2, String userName2) throws UserAccountAssociationException {

        boolean valid = false;

        try (Connection dbConnection = IdentityDatabaseUtil.getDBConnection();
             PreparedStatement preparedStatement = dbConnection.prepareStatement(UserAccountAssociationConstants
                     .SQLQueries.IS_VALID_ASSOCIATION)) {

            preparedStatement.setInt(1, tenantId1);
            preparedStatement.setString(2, domainName1);
            preparedStatement.setString(3, userName1);
            preparedStatement.setInt(4, tenantId2);
            preparedStatement.setString(5, domainName2);
            preparedStatement.setString(6, userName2);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {

                if (resultSet.next()) {
                    valid = resultSet.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            throw new UserAccountAssociationServerException(UserAccountAssociationConstants.ErrorMessages
                    .CHECK_ASSOCIATION_DB_ERROR.getDescription(), e);
        }

        return valid;
    }

    /**
     * Delete all associations of a tenant
     *
     * @param tenantId tenant ID
     * @throws UserAccountAssociationException
     */
    public void deleteUserAssociationsFromTenantId(int tenantId) throws UserAccountAssociationException {

        try (Connection dbConnection = IdentityDatabaseUtil.getDBConnection();
             PreparedStatement preparedStatement = dbConnection.prepareStatement(UserAccountAssociationConstants
                     .SQLQueries.DELETE_CONNECTION_FROM_TENANT_ID)) {

            preparedStatement.setInt(1, tenantId);
            preparedStatement.executeUpdate();

            if (!dbConnection.getAutoCommit()) {
                dbConnection.commit();
            }
        } catch (SQLException e) {
            throw new UserAccountAssociationServerException(UserAccountAssociationConstants.ErrorMessages
                    .ASSOCIATIONS_DELETE_DB_ERROR.getDescription(), e);
        }
    }

    /**
     * Update domain name of association
     *
     * @param tenantId          Tenant ID
     * @param currentDomainName Old domain name
     * @param newDomainName     New domain name
     * @throws UserAccountAssociationException
     */
    public void updateDomainNameOfAssociations(int tenantId, String currentDomainName, String newDomainName) throws
            UserAccountAssociationException {

        try (Connection dbConnection = IdentityDatabaseUtil.getDBConnection();
             PreparedStatement preparedStatement = dbConnection.prepareStatement(UserAccountAssociationConstants
                     .SQLQueries.UPDATE_USER_DOMAIN_NAME)) {

            preparedStatement.setString(1, newDomainName);
            preparedStatement.setString(2, currentDomainName);
            preparedStatement.setInt(3, tenantId);
            preparedStatement.executeUpdate();

            if (!dbConnection.getAutoCommit()) {
                dbConnection.commit();
            }
        } catch (SQLException e) {
            throw new UserAccountAssociationServerException(String.format(UserAccountAssociationConstants.ErrorMessages
                    .ERROR_UPDATE_DOMAIN_NAME.getDescription(), currentDomainName, tenantId), e);
        }
    }

    /**
     * Delete all associations of a domain
     *
     * @param tenantId   Tenant ID
     * @param domainName Domain name
     * @throws UserAccountAssociationException
     */
    public void deleteAssociationsFromDomain(int tenantId, String domainName) throws
            UserAccountAssociationException {

        try (Connection dbConnection = IdentityDatabaseUtil.getDBConnection();
             PreparedStatement preparedStatement = dbConnection.prepareStatement(UserAccountAssociationConstants
                     .SQLQueries.DELETE_USER_ASSOCIATION_FROM_DOMAIN)) {

            preparedStatement.setInt(1, tenantId);
            preparedStatement.setString(2, domainName);
            preparedStatement.executeUpdate();

            if (!dbConnection.getAutoCommit()) {
                dbConnection.commit();
            }
        } catch (SQLException e) {
            throw new UserAccountAssociationServerException(String.format(UserAccountAssociationConstants.ErrorMessages
                    .ERROR_DELETE_ASSOC_FROM_DOMAIN_NAME.getDescription(), domainName, tenantId), e);
        }
    }

    private static class LazyHolder {
        private static final UserAccountAssociationDAO INSTANCE = new UserAccountAssociationDAO();
    }

}