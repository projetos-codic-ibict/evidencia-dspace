/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStream;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.CharEncoding;
import org.apache.commons.io.IOUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.dspace.app.rest.model.ViewEventRest;
import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.builder.BitstreamBuilder;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.builder.SiteBuilder;
import org.dspace.content.Bitstream;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.content.Site;
import org.dspace.statistics.SolrStatisticsCore;
import org.dspace.utils.DSpace;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ViewEventRestRepositoryIT extends AbstractControllerIntegrationTest {

    @Autowired
    private ObjectMapper mapper;

    private final SolrStatisticsCore solrStatisticsCore = new DSpace().getSingletonService(SolrStatisticsCore.class);

    @Test
    public void findAllTestThrowNotImplementedException() throws Exception {

        getClient().perform(get("/api/statistics/viewevents"))
                   .andExpect(status().is(405));
    }
    @Test
    public void findOneTestThrowNotImplementedException() throws Exception {

        getClient().perform(get("/api/statistics/viewevents/" + UUID.randomUUID()))
                   .andExpect(status().is(405));
    }

    @Test
    public void postTestSucces() throws Exception {

        context.turnOffAuthorisationSystem();

        //** GIVEN **
        //1. A community-collection structure with one parent community with sub-community and two collections.
        parentCommunity = CommunityBuilder.createCommunity(context)
                                          .withName("Parent Community")
                                          .build();
        Community child1 = CommunityBuilder.createSubCommunity(context, parentCommunity)
                                           .withName("Sub Community")
                                           .build();
        Collection col1 = CollectionBuilder.createCollection(context, child1).withName("Collection 1").build();
        Collection col2 = CollectionBuilder.createCollection(context, child1).withName("Collection 2").build();

        //2. Three public items that are readable by Anonymous with different subjects
        Item publicItem1 = ItemBuilder.createItem(context, col1)
                                      .withTitle("Public item 1")
                                      .withIssueDate("2017-10-17")
                                      .withAuthor("Smith, Donald").withAuthor("Doe, John")
                                      .withSubject("ExtraEntry")
                                      .build();

        context.restoreAuthSystemState();

        ViewEventRest viewEventRest = new ViewEventRest();
        viewEventRest.setTargetType("item");
        viewEventRest.setTargetId(publicItem1.getID());

        getClient().perform(post("/api/statistics/viewevents")
                                                .content(mapper.writeValueAsBytes(viewEventRest))
                                                .contentType(contentType))
                   .andExpect(status().isCreated());

    }

    @Test
    public void postTestInvalidUUIDUnprocessableEntityException() throws Exception {

        context.turnOffAuthorisationSystem();

        //** GIVEN **
        //1. A community-collection structure with one parent community with sub-community and two collections.
        parentCommunity = CommunityBuilder.createCommunity(context)
                                          .withName("Parent Community")
                                          .build();
        Community child1 = CommunityBuilder.createSubCommunity(context, parentCommunity)
                                           .withName("Sub Community")
                                           .build();
        Collection col1 = CollectionBuilder.createCollection(context, child1).withName("Collection 1").build();
        Collection col2 = CollectionBuilder.createCollection(context, child1).withName("Collection 2").build();

        //2. Three public items that are readable by Anonymous with different subjects
        Item publicItem1 = ItemBuilder.createItem(context, col1)
                                      .withTitle("Public item 1")
                                      .withIssueDate("2017-10-17")
                                      .withAuthor("Smith, Donald").withAuthor("Doe, John")
                                      .withSubject("ExtraEntry")
                                      .build();

        context.restoreAuthSystemState();

        ViewEventRest viewEventRest = new ViewEventRest();
        viewEventRest.setTargetType("item");
        viewEventRest.setTargetId(UUID.randomUUID());

        getClient().perform(post("/api/statistics/viewevents")
                                .content(mapper.writeValueAsBytes(viewEventRest))
                                .contentType(contentType))
                   .andExpect(status().isUnprocessableEntity());

    }

    @Test
    public void postTestNoUUIDBadRequestException() throws Exception {

        context.turnOffAuthorisationSystem();

        //** GIVEN **
        //1. A community-collection structure with one parent community with sub-community and two collections.
        parentCommunity = CommunityBuilder.createCommunity(context)
                                          .withName("Parent Community")
                                          .build();
        Community child1 = CommunityBuilder.createSubCommunity(context, parentCommunity)
                                           .withName("Sub Community")
                                           .build();
        Collection col1 = CollectionBuilder.createCollection(context, child1).withName("Collection 1").build();
        Collection col2 = CollectionBuilder.createCollection(context, child1).withName("Collection 2").build();

        //2. Three public items that are readable by Anonymous with different subjects
        Item publicItem1 = ItemBuilder.createItem(context, col1)
                                      .withTitle("Public item 1")
                                      .withIssueDate("2017-10-17")
                                      .withAuthor("Smith, Donald").withAuthor("Doe, John")
                                      .withSubject("ExtraEntry")
                                      .build();

        context.restoreAuthSystemState();

        ViewEventRest viewEventRest = new ViewEventRest();
        viewEventRest.setTargetType("item");
        viewEventRest.setTargetId(null);

        getClient().perform(post("/api/statistics/viewevents")
                                .content(mapper.writeValueAsBytes(viewEventRest))
                                .contentType(contentType))
                   .andExpect(status().isBadRequest());

    }

    @Test
    public void postTestNoTargetTypeBadRequestException() throws Exception {

        context.turnOffAuthorisationSystem();

        //** GIVEN **
        //1. A community-collection structure with one parent community with sub-community and two collections.
        parentCommunity = CommunityBuilder.createCommunity(context)
                                          .withName("Parent Community")
                                          .build();
        Community child1 = CommunityBuilder.createSubCommunity(context, parentCommunity)
                                           .withName("Sub Community")
                                           .build();
        Collection col1 = CollectionBuilder.createCollection(context, child1).withName("Collection 1").build();
        Collection col2 = CollectionBuilder.createCollection(context, child1).withName("Collection 2").build();

        //2. Three public items that are readable by Anonymous with different subjects
        Item publicItem1 = ItemBuilder.createItem(context, col1)
                                      .withTitle("Public item 1")
                                      .withIssueDate("2017-10-17")
                                      .withAuthor("Smith, Donald").withAuthor("Doe, John")
                                      .withSubject("ExtraEntry")
                                      .build();

        context.restoreAuthSystemState();

        ViewEventRest viewEventRest = new ViewEventRest();
        viewEventRest.setTargetType(null);
        viewEventRest.setTargetId(publicItem1.getID());

        getClient().perform(post("/api/statistics/viewevents")
                                .content(mapper.writeValueAsBytes(viewEventRest))
                                .contentType(contentType))
                   .andExpect(status().isBadRequest());

    }

    @Test
    public void postTestWrongTargetTypeBadRequestException() throws Exception {

        context.turnOffAuthorisationSystem();

        //** GIVEN **
        //1. A community-collection structure with one parent community with sub-community and two collections.
        parentCommunity = CommunityBuilder.createCommunity(context)
                                          .withName("Parent Community")
                                          .build();
        Community child1 = CommunityBuilder.createSubCommunity(context, parentCommunity)
                                           .withName("Sub Community")
                                           .build();
        Collection col1 = CollectionBuilder.createCollection(context, child1).withName("Collection 1").build();
        Collection col2 = CollectionBuilder.createCollection(context, child1).withName("Collection 2").build();

        //2. Three public items that are readable by Anonymous with different subjects
        Item publicItem1 = ItemBuilder.createItem(context, col1)
                                      .withTitle("Public item 1")
                                      .withIssueDate("2017-10-17")
                                      .withAuthor("Smith, Donald").withAuthor("Doe, John")
                                      .withSubject("ExtraEntry")
                                      .build();

        context.restoreAuthSystemState();

        ViewEventRest viewEventRest = new ViewEventRest();
        viewEventRest.setTargetType("aezazeaezea");
        viewEventRest.setTargetId(publicItem1.getID());

        getClient().perform(post("/api/statistics/viewevents")
                                .content(mapper.writeValueAsBytes(viewEventRest))
                                .contentType(contentType))
                   .andExpect(status().isBadRequest());

    }

    @Test
    public void postBitstreamTestSucces() throws Exception {

        context.turnOffAuthorisationSystem();

        //** GIVEN **
        //1. A community-collection structure with one parent community with sub-community and two collections.
        parentCommunity = CommunityBuilder.createCommunity(context)
                                          .withName("Parent Community")
                                          .build();
        Community child1 = CommunityBuilder.createSubCommunity(context, parentCommunity)
                                           .withName("Sub Community")
                                           .build();
        Collection col1 = CollectionBuilder.createCollection(context, child1).withName("Collection 1").build();
        Collection col2 = CollectionBuilder.createCollection(context, child1).withName("Collection 2").build();

        //2. Three public items that are readable by Anonymous with different subjects
        Item publicItem1 = ItemBuilder.createItem(context, col1)
                                      .withTitle("Public item 1")
                                      .withIssueDate("2017-10-17")
                                      .withAuthor("Smith, Donald").withAuthor("Doe, John")
                                      .withSubject("ExtraEntry")
                                      .build();

        String bitstreamContent = "ThisIsSomeDummyText";
        //Add a bitstream to an item
        Bitstream bitstream = null;
        try (InputStream is = IOUtils.toInputStream(bitstreamContent, CharEncoding.UTF_8)) {
            bitstream = BitstreamBuilder.createBitstream(context, publicItem1, is)
                                        .withName("Bitstream")
                                        .withDescription("descr")
                                        .withMimeType("text/plain")
                                        .build();
        }

        context.restoreAuthSystemState();

        ViewEventRest viewEventRest = new ViewEventRest();
        viewEventRest.setTargetType("bitstream");
        viewEventRest.setTargetId(bitstream.getID());

        getClient().perform(post("/api/statistics/viewevents")
                                .content(mapper.writeValueAsBytes(viewEventRest))
                                .contentType(contentType))
                   .andExpect(status().isCreated());
    }

    @Test
    public void postCollectionTestSucces() throws Exception {

        context.turnOffAuthorisationSystem();

        //** GIVEN **
        //1. A community-collection structure with one parent community with sub-community and two collections.
        parentCommunity = CommunityBuilder.createCommunity(context)
                                          .withName("Parent Community")
                                          .build();
        Community child1 = CommunityBuilder.createSubCommunity(context, parentCommunity)
                                           .withName("Sub Community")
                                           .build();
        Collection col1 = CollectionBuilder.createCollection(context, child1).withName("Collection 1").build();
        Collection col2 = CollectionBuilder.createCollection(context, child1).withName("Collection 2").build();

        //2. Three public items that are readable by Anonymous with different subjects
        Item publicItem1 = ItemBuilder.createItem(context, col1)
                                      .withTitle("Public item 1")
                                      .withIssueDate("2017-10-17")
                                      .withAuthor("Smith, Donald").withAuthor("Doe, John")
                                      .withSubject("ExtraEntry")
                                      .build();

        String bitstreamContent = "ThisIsSomeDummyText";
        //Add a bitstream to an item
        Bitstream bitstream = null;
        try (InputStream is = IOUtils.toInputStream(bitstreamContent, CharEncoding.UTF_8)) {
            bitstream = BitstreamBuilder.createBitstream(context, publicItem1, is)
                                        .withName("Bitstream")
                                        .withDescription("descr")
                                        .withMimeType("text/plain")
                                        .build();
        }

        context.restoreAuthSystemState();

        ViewEventRest viewEventRest = new ViewEventRest();
        viewEventRest.setTargetType("collection");
        viewEventRest.setTargetId(col1.getID());

        getClient().perform(post("/api/statistics/viewevents")
                                .content(mapper.writeValueAsBytes(viewEventRest))
                                .contentType(contentType))
                   .andExpect(status().isCreated());

    }

    @Test
    public void postCommunityTestSucces() throws Exception {

        context.turnOffAuthorisationSystem();

        //** GIVEN **
        //1. A community-collection structure with one parent community with sub-community and two collections.
        parentCommunity = CommunityBuilder.createCommunity(context)
                                          .withName("Parent Community")
                                          .build();
        Community child1 = CommunityBuilder.createSubCommunity(context, parentCommunity)
                                           .withName("Sub Community")
                                           .build();
        Collection col1 = CollectionBuilder.createCollection(context, child1).withName("Collection 1").build();
        Collection col2 = CollectionBuilder.createCollection(context, child1).withName("Collection 2").build();

        //2. Three public items that are readable by Anonymous with different subjects
        Item publicItem1 = ItemBuilder.createItem(context, col1)
                                      .withTitle("Public item 1")
                                      .withIssueDate("2017-10-17")
                                      .withAuthor("Smith, Donald").withAuthor("Doe, John")
                                      .withSubject("ExtraEntry")
                                      .build();

        String bitstreamContent = "ThisIsSomeDummyText";
        //Add a bitstream to an item
        Bitstream bitstream = null;
        try (InputStream is = IOUtils.toInputStream(bitstreamContent, CharEncoding.UTF_8)) {
            bitstream = BitstreamBuilder.createBitstream(context, publicItem1, is)
                                        .withName("Bitstream")
                                        .withDescription("descr")
                                        .withMimeType("text/plain")
                                        .build();
        }

        context.restoreAuthSystemState();

        ViewEventRest viewEventRest = new ViewEventRest();
        viewEventRest.setTargetType("community");
        viewEventRest.setTargetId(child1.getID());

        getClient().perform(post("/api/statistics/viewevents")
                                .content(mapper.writeValueAsBytes(viewEventRest))
                                .contentType(contentType))
                   .andExpect(status().isCreated());

    }

    @Test
    public void postSiteTestSucces() throws Exception {

        context.turnOffAuthorisationSystem();

        //** GIVEN **
        //1. A community-collection structure with one parent community with sub-community and two collections.
        parentCommunity = CommunityBuilder.createCommunity(context)
                                          .withName("Parent Community")
                                          .build();
        Community child1 = CommunityBuilder.createSubCommunity(context, parentCommunity)
                                           .withName("Sub Community")
                                           .build();
        Collection col1 = CollectionBuilder.createCollection(context, child1).withName("Collection 1").build();
        Collection col2 = CollectionBuilder.createCollection(context, child1).withName("Collection 2").build();

        //2. Three public items that are readable by Anonymous with different subjects
        Item publicItem1 = ItemBuilder.createItem(context, col1)
                                      .withTitle("Public item 1")
                                      .withIssueDate("2017-10-17")
                                      .withAuthor("Smith, Donald").withAuthor("Doe, John")
                                      .withSubject("ExtraEntry")
                                      .build();

        String bitstreamContent = "ThisIsSomeDummyText";
        //Add a bitstream to an item
        Bitstream bitstream = null;
        try (InputStream is = IOUtils.toInputStream(bitstreamContent, CharEncoding.UTF_8)) {
            bitstream = BitstreamBuilder.createBitstream(context, publicItem1, is)
                                        .withName("Bitstream")
                                        .withDescription("descr")
                                        .withMimeType("text/plain")
                                        .build();

        }
        Site site = SiteBuilder.createSite(context).build();

        context.restoreAuthSystemState();

        ViewEventRest viewEventRest = new ViewEventRest();
        viewEventRest.setTargetType("site");
        viewEventRest.setTargetId(site.getID());

        getClient().perform(post("/api/statistics/viewevents")
                                .content(mapper.writeValueAsBytes(viewEventRest))
                                .contentType(contentType))
                   .andExpect(status().isCreated());

    }


    @Test
    public void postTestAuthenticatedUserSuccess() throws Exception {

        context.turnOffAuthorisationSystem();

        //** GIVEN **
        //1. A community-collection structure with one parent community with sub-community and two collections.
        parentCommunity = CommunityBuilder.createCommunity(context)
                                          .withName("Parent Community")
                                          .build();
        Community child1 = CommunityBuilder.createSubCommunity(context, parentCommunity)
                                           .withName("Sub Community")
                                           .build();
        Collection col1 = CollectionBuilder.createCollection(context, child1).withName("Collection 1").build();
        Collection col2 = CollectionBuilder.createCollection(context, child1).withName("Collection 2").build();

        //2. Three public items that are readable by Anonymous with different subjects
        Item publicItem1 = ItemBuilder.createItem(context, col1)
                                      .withTitle("Public item 1")
                                      .withIssueDate("2017-10-17")
                                      .withAuthor("Smith, Donald").withAuthor("Doe, John")
                                      .withSubject("ExtraEntry")
                                      .build();

        context.restoreAuthSystemState();

        ViewEventRest viewEventRest = new ViewEventRest();
        viewEventRest.setTargetType("item");
        viewEventRest.setTargetId(publicItem1.getID());

        String token = getAuthToken(eperson.getEmail(), password);

        getClient(token).perform(post("/api/statistics/viewevents")
                                .content(mapper.writeValueAsBytes(viewEventRest))
                                .contentType(contentType))
                   .andExpect(status().isCreated());

    }

    @Test
    public void postTestReferrer() throws Exception {

        context.turnOffAuthorisationSystem();

        //** GIVEN **
        //1. A community-collection structure with one parent community with sub-community and two collections.
        parentCommunity = CommunityBuilder.createCommunity(context)
                .withName("Parent Community")
                .build();
        Community child1 = CommunityBuilder.createSubCommunity(context, parentCommunity)
                .withName("Sub Community")
                .build();
        Collection col1 = CollectionBuilder.createCollection(context, child1).withName("Collection 1").build();
        Collection col2 = CollectionBuilder.createCollection(context, child1).withName("Collection 2").build();

        //2. Three public items that are readable by Anonymous with different subjects
        Item publicItem1 = ItemBuilder.createItem(context, col1)
                .withTitle("Public item 1")
                .withIssueDate("2017-10-17")
                .withAuthor("Smith, Donald").withAuthor("Doe, John")
                .withSubject("ExtraEntry")
                .build();

        context.restoreAuthSystemState();

        ViewEventRest viewEventRest = new ViewEventRest();
        viewEventRest.setTargetType("item");
        viewEventRest.setTargetId(publicItem1.getID());
        viewEventRest.setReferrer("test-referrer");

        getClient().perform(post("/api/statistics/viewevents")
                        .content(mapper.writeValueAsBytes(viewEventRest))
                        .contentType(contentType))
                .andExpect(status().isCreated());
        solrStatisticsCore.getSolr().commit();

        // Query all statistics and verify it contains a document with the correct referrer
        SolrQuery solrQuery = new SolrQuery("*:*");
        QueryResponse queryResponse = solrStatisticsCore.getSolr().query(solrQuery);
        SolrDocumentList responseList = queryResponse.getResults();
        assertEquals(1, responseList.size());
        assertEquals("test-referrer", responseList.get(0).get("referrer"));
    }

    @Test
    public void postReferenceCopyTestLogsReferenceCopyEvent() throws Exception {
        Item publicItem = createPublicItem();

        getClient().perform(post("/api/statistics/viewevents")
                                .content(mapper.writeValueAsBytes(referenceCopyEvent(publicItem)))
                                .contentType(contentType))
                   .andExpect(status().isCreated());

        assertEquals(1, countStatisticsDocs(publicItem, "reference_copy"));
        // A cópia da referência não pode contar como visita
        assertEquals(0, countStatisticsDocs(publicItem, "view"));
    }

    @Test
    public void postReferenceCopyTestSecondEventInsideIntervalIsRejected() throws Exception {
        Item publicItem = createPublicItem();
        byte[] body = mapper.writeValueAsBytes(referenceCopyEvent(publicItem));

        getClient().perform(post("/api/statistics/viewevents").content(body).contentType(contentType))
                   .andExpect(status().isCreated());
        getClient().perform(post("/api/statistics/viewevents").content(body).contentType(contentType))
                   .andExpect(status().isTooManyRequests());

        assertEquals(1, countStatisticsDocs(publicItem, "reference_copy"));
    }

    @Test
    public void postReferenceCopyTestAdminIsNotCounted() throws Exception {
        Item publicItem = createPublicItem();
        String adminToken = getAuthToken(admin.getEmail(), password);

        getClient(adminToken).perform(post("/api/statistics/viewevents")
                                          .content(mapper.writeValueAsBytes(referenceCopyEvent(publicItem)))
                                          .contentType(contentType))
                             .andExpect(status().isCreated());

        assertEquals(0, countStatisticsDocs(publicItem, "reference_copy"));
    }

    @Test
    public void totalReferenceCopiesReportReturnsTheLoggedCopies() throws Exception {
        Item publicItem = createPublicItem();

        getClient().perform(post("/api/statistics/viewevents")
                                .content(mapper.writeValueAsBytes(referenceCopyEvent(publicItem)))
                                .contentType(contentType))
                   .andExpect(status().isCreated());
        solrStatisticsCore.getSolr().commit();

        String adminToken = getAuthToken(admin.getEmail(), password);
        getClient(adminToken).perform(get("/api/statistics/usagereports/" + publicItem.getID()
                                              + "_TotalReferenceCopies"))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$.report-type", is("TotalReferenceCopies")))
                             .andExpect(jsonPath("$.points[0].values.views", is(1)));
    }

    private Item createPublicItem() throws Exception {
        context.turnOffAuthorisationSystem();
        parentCommunity = CommunityBuilder.createCommunity(context).withName("Parent Community").build();
        Collection col = CollectionBuilder.createCollection(context, parentCommunity)
                                          .withName("Collection 1").build();
        Item publicItem = ItemBuilder.createItem(context, col)
                                     .withTitle("Public item 1")
                                     .withIssueDate("2017-10-17")
                                     .withAuthor("Smith, Donald")
                                     .build();
        context.restoreAuthSystemState();
        return publicItem;
    }

    private ViewEventRest referenceCopyEvent(Item item) {
        ViewEventRest viewEventRest = new ViewEventRest();
        viewEventRest.setTargetType("item");
        viewEventRest.setTargetId(item.getID());
        viewEventRest.setEventType("reference_copy");
        return viewEventRest;
    }

    private long countStatisticsDocs(Item item, String statisticsType) throws Exception {
        solrStatisticsCore.getSolr().commit();
        SolrQuery solrQuery = new SolrQuery("id:" + item.getID() + " AND statistics_type:" + statisticsType);
        return solrStatisticsCore.getSolr().query(solrQuery).getResults().getNumFound();
    }


}
