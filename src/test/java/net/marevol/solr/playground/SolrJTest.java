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
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import org.codelibs.curl.Curl;
import org.codelibs.curl.CurlResponse;
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

    static void createConfig(final String configName) throws IOException {
        final Path configPath = Paths.get("src/test/resources/configset/" + configName);
        final Path configSetPath = Files.createTempFile("configset", ".zip");
        try (final ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(configSetPath))) {
            Files.walkFileTree(configPath, new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) throws IOException {
                    final String name = configPath.relativize(dir).toString();
                    if (!name.isEmpty()) {
                        out.putNextEntry(new ZipEntry(name + "/"));
                        out.closeEntry();
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                    final Path targetFile = configPath.relativize(file);
                    out.putNextEntry(new ZipEntry(targetFile.toString()));
                    out.write(Files.readAllBytes(file));
                    out.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        try (final InputStream in = Files.newInputStream(configSetPath);
                final CurlResponse response = Curl.post(getSolrUrl() + "/admin/configs").header("Content-Type", "application/octed-stream")
                        .param("action", "UPLOAD").param("name", configName).body(in).execute()) {
            if (response.getHttpStatusCode() != 200) {
                throw new IOException("Failed to create " + configName + " from " + configPath + ": " + response.getContentAsString());
            }
        }

        Files.delete(configSetPath);
    }

    static void deleteConfig(final String configName) throws IOException {
        try (CurlResponse response =
                Curl.post(getSolrUrl() + "/admin/configs").param("action", "DELETE").param("name", configName).execute()) {
            if (response.getHttpStatusCode() != 200) {
                throw new IOException("Failed to delete " + configName + ": " + response.getContentAsString());
            }
        }
    }

    static void createCollection(final String configName, final String collectionName, final int numShards) throws IOException {
        try (CurlResponse response = Curl.post(getSolrUrl() + "/admin/collections").param("action", "CREATE").param("name", collectionName)
                .param("numShards", Integer.toString(numShards)).param("collection.configName", configName).execute()) {
            if (response.getHttpStatusCode() != 200) {
                throw new IOException("Failed to create " + collectionName + " with " + configName + ": " + response.getContentAsString());
            }
        }
    }

    static void deleteCollection(final String collectionName) throws IOException {
        try (CurlResponse response =
                Curl.post(getSolrUrl() + "/admin/collections").param("action", "DELETE").param("name", collectionName).execute()) {
            if (response.getHttpStatusCode() != 200) {
                throw new IOException("Failed to delete " + collectionName + ": " + response.getContentAsString());
            }
        }
    }

    @Test
    void test_run() throws Exception {
        final String configName = "playground";
        final String collectionName = "pgc8n";

        createConfig(configName);
        createCollection(configName, collectionName, 1);

        final String solrUrl = getSolrUrl();
        final SolrClient client = createSolrClient(solrUrl);

        final SolrPingResponse response = client.ping(collectionName);

        assertEquals(0, response.getStatus());

        deleteCollection(collectionName);
        deleteConfig(configName);
    }

    @Test
    void test_ping() throws Exception {
        final String solrUrl = getSolrUrl();
        final SolrClient client = createSolrClient(solrUrl);

        final SolrPingResponse response = client.ping("dummy");

        assertEquals(0, response.getStatus());
    }

}
