/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.replication.service.impl;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.app.client.DSpaceHttpClientFactory;
import org.dspace.replication.service.ReplicationService;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * HTTP implementation of {@link ReplicationService}.
 */
public class ReplicationServiceImpl implements ReplicationService {

    private static final Logger LOGGER = LogManager.getLogger();
    
    private static final String API_BASE_URL_PROPERTY = "replication.api.base-url";
    private static final String ITEM_INGEST_PATH = "/api/files/dspace/item/";

    private final ConfigurationService configurationService;
    private final DSpaceHttpClientFactory httpClientFactory;

    /**
     * Creates the service with the dependencies used to contact the external API.
     *
     * @param configurationService DSpace configuration service
     * @param httpClientFactory configured HTTP client factory
     */
    @Autowired
    public ReplicationServiceImpl(ConfigurationService configurationService,
                                  DSpaceHttpClientFactory httpClientFactory) {
        this.configurationService = configurationService;
        this.httpClientFactory = httpClientFactory;
    }

    @Override
    public void replicateItem(UUID itemId) {
        URI endpoint;

        try 
        {
            endpoint = buildItemEndpoint(configurationService.getProperty(API_BASE_URL_PROPERTY), itemId);
        } 
        catch (IllegalArgumentException e) {
            LOGGER.error("Não foi possível montar a URL da API de replicação para o item {}", itemId, e);
            return;
        }

        HttpPost request = new HttpPost(endpoint);
        try (CloseableHttpClient httpClient = httpClientFactory.build();
             CloseableHttpResponse response = httpClient.execute(request)) 
        {
            int status = response.getStatusLine().getStatusCode();
            if (status >= 200 && status < 300) {
                LOGGER.info("Item {} enviado para replicação", itemId);
            } 
            else {
                LOGGER.error("A API de replicação retornou HTTP {} para o item {}", status, itemId);
            }
        } 
        catch (IOException e) {
            LOGGER.error("Falha ao enviar o item {} para a API de replicação", itemId, e);
        }
    }

    /**
     * Builds the ingestion endpoint for an item UUID.
     *
     * @param apiBaseUrl configured base URL of the replication API
     * @param itemId UUID of the DSpace item to ingest
     * @return endpoint to receive the item UUID
     */
    static URI buildItemEndpoint(String apiBaseUrl, UUID itemId) {
        if (apiBaseUrl == null || apiBaseUrl.isBlank()) {
            throw new IllegalArgumentException("A propriedade " + API_BASE_URL_PROPERTY + " não está configurada");
        }

        return URI.create(apiBaseUrl.replaceFirst("/+$", "") + ITEM_INGEST_PATH + itemId);
    }
}
