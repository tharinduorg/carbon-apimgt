/*
 *  Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.carbon.apimgt.impl.certificatemgt.reloader;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.impl.dto.TrustStoreDTO;
import org.wso2.carbon.apimgt.impl.internal.ServiceReferenceHolder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

/**
 * This class used to load new certificate file into Memory
 */
public class CertificateReLoader implements Runnable {

    private static final Log log = LogFactory.getLog(CertificateReLoader.class);

    @Override
    public void run() {

        TrustStoreDTO trustStoreDTO = CertificateReLoaderUtil.getTrustStore();
        if (trustStoreDTO != null) {
            File trustStoreFile = new File(trustStoreDTO.getLocation());
            try {
                long lastUpdatedTimeStamp = CertificateReLoaderUtil.getLastUpdatedTimeStamp();
                long lastModified = trustStoreFile.lastModified();
                if (lastUpdatedTimeStamp != lastModified) {
                    CertificateReLoaderUtil.setLastUpdatedTimeStamp(lastModified);
                    KeyStore trustStore;
                    try (FileInputStream localTrustStoreStream = new FileInputStream(trustStoreFile)) {
                        trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
                        trustStore.load(localTrustStoreStream, trustStoreDTO.getPassword());
                    }
                    ServiceReferenceHolder.getInstance().setListenerTrustStore(trustStore);
                }
            } catch (KeyStoreException | CertificateException | IOException | NoSuchAlgorithmException e) {
                log.error("Unable to find the certificate", e);
            }
        }
    }
}
