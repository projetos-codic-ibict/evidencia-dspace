/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.apache.commons.codec.CharEncoding.UTF_8;
import static org.apache.commons.io.IOUtils.toInputStream;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dspace.app.rest.model.ViewEventRest;
import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.app.rest.utils.PainelUsoUtils;
import org.dspace.builder.BitstreamBuilder;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Bitstream;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.services.ConfigurationService;
import org.dspace.statistics.factory.StatisticsServiceFactory;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Testes de integração de {@link PainelEstatisticasRestController}
 * ({@code GET /api/statistics/painel/uso}).
 */
public class PainelEstatisticasRestControllerIT extends AbstractControllerIntegrationTest {

    private static final String ENDPOINT = "/api/statistics/painel/uso";

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private PainelUsoUtils painelUsoUtils;

    @Autowired
    private ObjectMapper mapper;

    private Item itemVisitado;
    private Item itemNaoVisitado;
    private Bitstream bitstreamBaixado;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        // Cada teste comeca com o core statistics vazio (removeIndex faz commit imediato);
        // a ordem de execucao dos testes nao e garantida.
        StatisticsServiceFactory.getInstance().getSolrLoggerService().removeIndex("*:*");

        // Commit explicito no SolrLoggerServiceImpl#postView, como nos demais ITs de estatistica.
        configurationService.setProperty("solr-statistics.autoCommit", false);
        configurationService.setProperty("usage-statistics.authorization.admin.usage", false);
        // Sem cache: cada teste deve enxergar os eventos que acabou de registrar.
        configurationService.setProperty(PainelUsoUtils.PROP_CACHE_SEGUNDOS, 0);
        painelUsoUtils.invalidarCache();

        context.turnOffAuthorisationSystem();
        Community comunidade = CommunityBuilder.createCommunity(context).build();
        Collection colecao = CollectionBuilder.createCollection(context, comunidade).build();
        itemVisitado = ItemBuilder.createItem(context, colecao).withTitle("Item visitado").build();
        itemNaoVisitado = ItemBuilder.createItem(context, colecao).withTitle("Item nao visitado").build();
        bitstreamBaixado = BitstreamBuilder
            .createBitstream(context, itemVisitado, toInputStream("test", UTF_8))
            .withName("arquivo.pdf").build();
        context.restoreAuthSystemState();
    }

    @Test
    public void usoPublico_semEventos_retornaZeros() throws Exception {
        getClient().perform(get(ENDPOINT))
                   .andExpect(status().isOk())
                   .andExpect(jsonPath("$.totais.site", is(0)))
                   .andExpect(jsonPath("$.totais.documentos", is(0)))
                   .andExpect(jsonPath("$.totais.downloads", is(0)))
                   .andExpect(jsonPath("$.porItem", hasSize(0)))
                   .andExpect(jsonPath("$.termosBuscados", hasSize(0)))
                   .andExpect(jsonPath("$.cacheSegundos", is(0)));
    }

    @Test
    public void usoPublico_comViewsEDownloads_agrupaPorItem() throws Exception {
        // Duas visualizacoes do item e um download do seu bitstream.
        registrarView("item", itemVisitado.getID());
        registrarView("item", itemVisitado.getID());
        registrarView("bitstream", bitstreamBaixado.getID());

        String filtroItem = "$.porItem[?(@.id == '" + itemVisitado.getID() + "')]";
        getClient().perform(get(ENDPOINT))
                   .andExpect(status().isOk())
                   // O download (evento de bitstream) nao conta como visualizacao de pagina.
                   .andExpect(jsonPath("$.totais.site", is(2)))
                   .andExpect(jsonPath("$.totais.documentos", is(2)))
                   .andExpect(jsonPath("$.totais.downloads", is(1)))
                   .andExpect(jsonPath("$.porItem", hasSize(1)))
                   .andExpect(jsonPath(filtroItem + ".views", contains(2)))
                   .andExpect(jsonPath(filtroItem + ".downloads", contains(1)))
                   .andExpect(jsonPath("$.porItem[?(@.id == '" + itemNaoVisitado.getID() + "')]", hasSize(0)));
    }

    @Test
    public void usoRestritoAAdmin_anonimoRecebe401_adminRecebe200() throws Exception {
        configurationService.setProperty("usage-statistics.authorization.admin.usage", true);

        getClient().perform(get(ENDPOINT))
                   .andExpect(status().isUnauthorized());

        String tokenAdmin = getAuthToken(admin.getEmail(), password);
        getClient(tokenAdmin).perform(get(ENDPOINT))
                             .andExpect(status().isOk());
    }

    private void registrarView(String tipo, UUID alvo) throws Exception {
        ViewEventRest view = new ViewEventRest();
        view.setTargetType(tipo);
        view.setTargetId(alvo);
        getClient().perform(post("/api/statistics/viewevents")
                                .content(mapper.writeValueAsBytes(view))
                                .contentType(contentType))
                   .andExpect(status().isCreated());
    }
}
