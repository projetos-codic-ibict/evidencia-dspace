/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.replication.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.UUID;
import java.util.function.Consumer;

import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.dspace.app.client.DSpaceHttpClientFactory;
import org.dspace.services.ConfigurationService;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link ReplicationServiceImpl}.
 */
public class ReplicationServiceImplTest {

    @Test
    public void replicateItemPostsTheUuidToTheItemIngestionEndpoint() throws Exception {
        UUID itemId = UUID.fromString("6c14971d-a4ca-4b5e-a790-e5a73660f80f");
        
        ConfigurationService configurationService = mock(ConfigurationService.class);
        DSpaceHttpClientFactory httpClientFactory = mock(DSpaceHttpClientFactory.class);
        CloseableHttpClient httpClient = mock(CloseableHttpClient.class);
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        
        when(configurationService.getProperty("replication.api.base-url")).thenReturn("http://rdapp-api-mock:8000/");
        when(httpClientFactory.build()).thenReturn(httpClient);
        when(httpClient.execute(org.mockito.ArgumentMatchers.any(HttpPost.class))).thenReturn(response);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(statusLine.getStatusCode()).thenReturn(202);
        
        ReplicationServiceImpl replicationService =
            new ReplicationServiceImpl(configurationService, httpClientFactory);

        replicationService.replicateItem(itemId);

        ArgumentCaptor<HttpPost> requestCaptor = ArgumentCaptor.forClass(HttpPost.class);
        verify(httpClient).execute(requestCaptor.capture());
        
        HttpPost request = requestCaptor.getValue();
        assertEquals("POST", request.getMethod());
        assertEquals("/api/files/dspace/item/" + itemId, request.getURI().getPath());
        assertNull(request.getEntity());
    }

    @Test
    public void buildItemEndpointRemovesTrailingSlashesFromTheBaseUrl() {
        UUID itemId = UUID.fromString("6c14971d-a4ca-4b5e-a790-e5a73660f80f");
        URI endpoint = ReplicationServiceImpl.buildItemEndpoint("http://rdapp-api-mock:8000///", itemId);

        assertEquals("http://rdapp-api-mock:8000/api/files/dspace/item/" + itemId, endpoint.toString());
    }

    @Test
    public void getResponseMessageReadsTheApiErrorBody() throws Exception {
        String message = "{\"detail\":\"DSpace retornou HTTP 404 para o item.\"}";

        assertEquals(message, ReplicationServiceImpl.getResponseMessage(new StringEntity(message)));
    }

    @Test
    public void replicateItemUsesTemporaryInsecureTlsClientOnlyWhenEnabled() throws Exception {
        UUID itemId = UUID.fromString("6c14971d-a4ca-4b5e-a790-e5a73660f80f");

        ConfigurationService configurationService = mock(ConfigurationService.class);
        DSpaceHttpClientFactory httpClientFactory = mock(DSpaceHttpClientFactory.class);
        CloseableHttpClient httpClient = mock(CloseableHttpClient.class);
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);

        when(configurationService.getProperty("replication.api.base-url")).thenReturn("https://api.rdapp.comais.uft.edu.br");
        when(configurationService.getBooleanProperty("replication.api.insecure-tls", false)).thenReturn(true);
        when(httpClientFactory.build(org.mockito.ArgumentMatchers.<Consumer<HttpClientBuilder>>any())).thenReturn(httpClient);
        when(httpClient.execute(org.mockito.ArgumentMatchers.any(HttpPost.class))).thenReturn(response);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(statusLine.getStatusCode()).thenReturn(202);

        ReplicationServiceImpl replicationService =
            new ReplicationServiceImpl(configurationService, httpClientFactory);

        replicationService.replicateItem(itemId);

        verify(httpClientFactory).build(org.mockito.ArgumentMatchers.<Consumer<HttpClientBuilder>>any());
        verify(httpClientFactory, never()).build();
    }
}
