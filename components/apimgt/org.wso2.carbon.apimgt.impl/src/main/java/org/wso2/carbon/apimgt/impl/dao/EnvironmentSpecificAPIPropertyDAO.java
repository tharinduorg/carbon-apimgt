/*
 *  Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.carbon.apimgt.impl.dao;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.impl.dao.constants.EnvironmentSpecificAPIPropertyConstants;
import org.wso2.carbon.apimgt.impl.gatewayartifactsynchronizer.environmentspecificproperty.Environment;
import org.wso2.carbon.apimgt.impl.utils.APIMgtDBUtil;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * This class implements the DAO operations related to environment specific api properties.
 */
public class EnvironmentSpecificAPIPropertyDAO {

    private static EnvironmentSpecificAPIPropertyDAO INSTANCE = null;

    private EnvironmentSpecificAPIPropertyDAO() {

    }

    public static EnvironmentSpecificAPIPropertyDAO getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new EnvironmentSpecificAPIPropertyDAO();
        }
        return INSTANCE;
    }

    public void addOrUpdateEnvironmentSpecificAPIProperties(String apiUuid, String envUuid, String content)
            throws APIManagementException {
        boolean isConfigExist = isEnvironmentSpecificAPIPropertiesExist(apiUuid, envUuid);
        if (isConfigExist) {
            updateEnvironmentSpecificAPIProperties(apiUuid, envUuid, content);
        } else {
            addEnvironmentSpecificAPIProperties(apiUuid, envUuid, content);
        }
    }

    private void addEnvironmentSpecificAPIProperties(String apiUuid, String envUuid, String content)
            throws APIManagementException {
        try (Connection conn = APIMgtDBUtil.getConnection();
                PreparedStatement preparedStatement = conn.prepareStatement(
                        EnvironmentSpecificAPIPropertyConstants.ADD_ENVIRONMENT_SPECIFIC_API_PROPERTIES_SQL)) {
            preparedStatement.setString(1, UUID.randomUUID().toString());
            preparedStatement.setString(2, apiUuid);
            preparedStatement.setString(3, envUuid);
            preparedStatement.setBlob(4, new ByteArrayInputStream(content.getBytes()));
            preparedStatement.execute();
        } catch (SQLException e) {
            throw new APIManagementException("Error occurred when adding environment specific api properties", e);
        }
    }

    private void updateEnvironmentSpecificAPIProperties(String apiUuid, String envUuid, String content)
            throws APIManagementException {
        try (Connection conn = APIMgtDBUtil.getConnection();
                PreparedStatement preparedStatement = conn.prepareStatement(
                        EnvironmentSpecificAPIPropertyConstants.UPDATE_ENVIRONMENT_SPECIFIC_API_PROPERTIES_SQL)) {
            preparedStatement.setBlob(1, new ByteArrayInputStream(content.getBytes()));
            preparedStatement.setString(2, apiUuid);
            preparedStatement.setString(3, envUuid);
            preparedStatement.execute();
        } catch (SQLException e) {
            throw new APIManagementException("Error occurred when updating environment specific api properties", e);
        }
    }

    public String getEnvironmentSpecificAPIProperties(String apiUuid, String envUuid) throws APIManagementException {
        try (Connection conn = APIMgtDBUtil.getConnection();
                PreparedStatement preparedStatement = conn.prepareStatement(
                        EnvironmentSpecificAPIPropertyConstants.GET_ENVIRONMENT_SPECIFIC_API_PROPERTIES_SQL)) {
            preparedStatement.setString(1, apiUuid);
            preparedStatement.setString(2, envUuid);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                String propertyConfig = null;
                if (resultSet.next()) {
                    InputStream propertyConfigBlob = resultSet.getBinaryStream(1);
                    if (propertyConfigBlob != null) {
                        propertyConfig = APIMgtDBUtil.getStringFromInputStream(propertyConfigBlob);
                    }
                }
                return propertyConfig;
            }
        } catch (SQLException e) {
            throw new APIManagementException("Error occurred when getting environment specific api properties", e);
        }
    }

    private boolean isEnvironmentSpecificAPIPropertiesExist(String apiUuid, String envUuid)
            throws APIManagementException {
        try (Connection conn = APIMgtDBUtil.getConnection();
                PreparedStatement preparedStatement = conn.prepareStatement(
                        EnvironmentSpecificAPIPropertyConstants.IS_ENVIRONMENT_SPECIFIC_API_PROPERTIES_EXIST_SQL)) {
            preparedStatement.setString(1, apiUuid);
            preparedStatement.setString(2, envUuid);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return true;
                }
            }
        } catch (SQLException e) {
            throw new APIManagementException("Error occurred when checking environment specific api properties", e);
        }
        return false;
    }

    public Map<String, Map<String, Environment>> getEnvironmentSpecificAPIPropertiesOfAPIs(List<String> apiUuidS)
            throws APIManagementException {
        Map<String, org.wso2.carbon.apimgt.api.model.Environment> defaultEnvs = APIUtil.getReadOnlyEnvironments();
        List<String> envIds = defaultEnvs.values().stream().map(org.wso2.carbon.apimgt.api.model.Environment::getUuid)
                .collect(Collectors.toList());
        Map<String, Map<String, Environment>> mgEnvs = getMGEnvironmentSpecificAPIPropertiesOfAPIs(apiUuidS);
        return getDefaultEnvironmentSpecificAPIPropertiesOfAPIs(apiUuidS, envIds, mgEnvs);
    }

    /**
     * Getting the api configs related MGs
     *
     * @param apiUuidS
     * @return
     * @throws APIManagementException
     */
    private Map<String, Map<String, Environment>> getMGEnvironmentSpecificAPIPropertiesOfAPIs(List<String> apiUuidS)
            throws APIManagementException {
        final String query = EnvironmentSpecificAPIPropertyConstants.GET_ENVIRONMENT_SPECIFIC_API_PROPERTIES_BY_APIS_SQL
                .replaceAll("_API_ID_LIST_", String.join(",", Collections.nCopies(apiUuidS.size(), "?")));

        Map<String, Map<String, Environment>> apiEnvironmentMap = new HashMap<>();
        try (Connection conn = APIMgtDBUtil.getConnection();
                PreparedStatement preparedStatement = conn.prepareStatement(query)) {
            int index = 1;
            for (String apiId : apiUuidS) {
                preparedStatement.setString(index++, apiId);
            }
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    String envId = resultSet.getString(1);
                    String envName = resultSet.getString(2);
                    String apiId = resultSet.getString(3);
                    JsonObject jsonConfig = null;
                    InputStream propertyConfigBlob = resultSet.getBinaryStream(4);
                    if (propertyConfigBlob != null) {
                        String apiJsonConfig = APIMgtDBUtil.getStringFromInputStream(propertyConfigBlob);
                        jsonConfig = new Gson().fromJson(apiJsonConfig, JsonObject.class);
                    }

                    Map<String, Environment> environmentMap;
                    Environment environment;
                    if (apiEnvironmentMap.containsKey(apiId)) {
                        environmentMap = apiEnvironmentMap.get(apiId);
                    } else {
                        environmentMap = new HashMap<>();
                        apiEnvironmentMap.put(apiId, environmentMap);
                    }
                    environment = new Environment();
                    environment.setEnvId(envId);
                    environment.setEnvName(envName);
                    environment.setConfigs(jsonConfig);
                    environmentMap.put(envName, environment);
                }
            }
        } catch (SQLException e) {
            throw new APIManagementException("Error occurred when getting MG environment specific api properties", e);
        }
        return apiEnvironmentMap;
    }

    /**
     * Getting the api configs related to default environments.
     *
     * @param apiUuidS
     * @param envIds
     * @param apiEnvironmentMap
     * @return
     * @throws APIManagementException
     */
    private Map<String, Map<String, Environment>> getDefaultEnvironmentSpecificAPIPropertiesOfAPIs(
            List<String> apiUuidS, List<String> envIds, Map<String, Map<String, Environment>> apiEnvironmentMap)
            throws APIManagementException {
        final String query =
                EnvironmentSpecificAPIPropertyConstants.GET_ENVIRONMENT_SPECIFIC_API_PROPERTIES_BY_APIS_ENVS_SQL
                        .replaceAll("_ENV_ID_LIST_", String.join(",", Collections.nCopies(envIds.size(), "?")))
                        .replaceAll("_API_ID_LIST_", String.join(",", Collections.nCopies(apiUuidS.size(), "?")));

        try (Connection conn = APIMgtDBUtil.getConnection();
                PreparedStatement preparedStatement = conn.prepareStatement(query)) {
            int index = 1;
            for (String envId : envIds) {
                preparedStatement.setString(index++, envId);
            }
            for (String apiId : apiUuidS) {
                preparedStatement.setString(index++, apiId);
            }
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    String envId = resultSet.getString(1);
                    String envName = envId; // for default envs envId and envName is same
                    String apiId = resultSet.getString(2);
                    JsonObject jsonConfig = null;
                    InputStream propertyConfigBlob = resultSet.getBinaryStream(3);
                    if (propertyConfigBlob != null) {
                        String apiJsonConfig = APIMgtDBUtil.getStringFromInputStream(propertyConfigBlob);
                        jsonConfig = new Gson().fromJson(apiJsonConfig, JsonObject.class);
                    }
                    Map<String, Environment> environmentMap;
                    Environment environment;
                    if (apiEnvironmentMap.containsKey(apiId)) {
                        environmentMap = apiEnvironmentMap.get(apiId);
                    } else {
                        environmentMap = new HashMap<>();
                        apiEnvironmentMap.put(apiId, environmentMap);
                    }
                    environment = new Environment();
                    environment.setEnvId(envId);
                    environment.setEnvName(envName);
                    environment.setConfigs(jsonConfig);
                    environmentMap.put(envName, environment);
                }
            }
        } catch (SQLException e) {
            throw new APIManagementException("Error occurred when getting default environment specific api properties",
                    e);
        }
        return apiEnvironmentMap;
    }
}
