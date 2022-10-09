/*
 * Copyright 2012-2022 CodeLibs Project and the Others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package net.marevol.solr.playground;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.logging.Logger;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.SolrContainer;
import org.testcontainers.utility.DockerImageName;

class SolrJTest {
    static final Logger logger = Logger.getLogger(SolrJTest.class.getName());

    static final String version = "9.0.0";

    static final String imageTag = "solr:" + version;

    static SolrContainer container;

    @BeforeAll
    static void setUpAll() {
        container = new SolrContainer(DockerImageName.parse(imageTag));
        container.start();
        waitFor();
    }

    static SolrClient createSolrClient(final String url) {
        return new Http2SolrClient.Builder(url).build();
    }

    static String getSolrUrl() {
        return "http://" + container.getHost() + ":" + container.getSolrPort() + "/solr";
    }

    static void waitFor() {
        final String url = getSolrUrl();
        final SolrClient client = createSolrClient(url);
        logger.info("Solr " + version + ": " + client);
        for (int i = 0; i < 10; i++) {
            try {
                final SolrPingResponse response = client.ping("dummy");
                if (response.getStatus() == 0) {
                    logger.info(url + " is available.");
                    break;
                }
            } catch (SolrServerException | IOException e) {
                logger.warning(e.getMessage());
            }
            try {
                logger.info("Waiting for " + url);
                Thread.sleep(1000L);
            } catch (final InterruptedException e) {
                // nothing
            }
        }
    }

    @BeforeEach
    void setUp() {
        // TODO
    }

    @AfterEach
    void tearDown() {
        // TODO
    }

    @AfterAll
    static void tearDownAll() {
        container.stop();
    }

    @Test
    void test_ping() throws Exception {
        final String solrUrl = getSolrUrl();
        final SolrClient client = createSolrClient(solrUrl);

        final SolrPingResponse response = client.ping("dummy");

        assertEquals(0, response.getStatus());
    }

}
