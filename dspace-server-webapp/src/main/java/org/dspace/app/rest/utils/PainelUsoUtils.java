/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.utils;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.solr.client.solrj.SolrServerException;
import org.dspace.app.rest.model.PainelUsoRest;
import org.dspace.core.Constants;
import org.dspace.services.ConfigurationService;
import org.dspace.statistics.ObjectCount;
import org.dspace.statistics.SolrLoggerServiceImpl.StatisticsType;
import org.dspace.statistics.factory.StatisticsServiceFactory;
import org.dspace.statistics.service.SolrLoggerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Monta o {@link PainelUsoRest} a partir do core {@code statistics} do Solr.
 *
 * <p>Todas as consultas passam por {@link SolrLoggerService#query}, que já aplica
 * os filtros padrão do DSpace ({@code -isBot:true} e o filtro de bundles de
 * {@code solr-statistics.query.filter.bundles}), de modo que os números batem
 * com os relatórios nativos de {@code UsageReportUtils}.</p>
 *
 * <p>A resposta é cacheada em memória por
 * {@value #PROP_CACHE_SEGUNDOS} segundos (0 desliga o cache): as
 * estatísticas de uso não precisam ser calculadas a cada requisição do painel.</p>
 */
@Component
public class PainelUsoUtils {

    public static final String PROP_CACHE_SEGUNDOS = "usage-statistics.painel.cache.seconds";
    public static final String PROP_MAX_ITENS = "usage-statistics.painel.maxItens";
    public static final String PROP_MAX_TERMOS = "usage-statistics.painel.maxTermos";

    private static final int CACHE_SEGUNDOS_PADRAO = 300;
    private static final int MAX_ITENS_PADRAO = 100000;
    private static final int MAX_TERMOS_PADRAO = 50;

    private static final String FQ_VIEW = "statistics_type:" + StatisticsType.VIEW.text();
    private static final String FQ_SEARCH = "statistics_type:" + StatisticsType.SEARCH.text();

    @Autowired
    private ConfigurationService configurationService;

    private PainelUsoRest cache;
    private long cacheExpiraEm;

    /**
     * Devolve o relatório de uso, reaproveitando a última resposta enquanto o
     * cache estiver válido.
     *
     * @return relatório de uso do painel
     * @throws SolrServerException erro do Solr
     * @throws IOException         erro de comunicação com o Solr
     */
    public synchronized PainelUsoRest getPainelUso() throws SolrServerException, IOException {
        int ttl = configurationService.getIntProperty(PROP_CACHE_SEGUNDOS, CACHE_SEGUNDOS_PADRAO);
        long agora = System.currentTimeMillis();
        if (cache != null && ttl > 0 && agora < cacheExpiraEm) {
            return cache;
        }
        PainelUsoRest relatorio = montar();
        relatorio.setCacheSegundos(Math.max(ttl, 0));
        cache = relatorio;
        cacheExpiraEm = agora + Math.max(ttl, 0) * 1000L;
        return relatorio;
    }

    /** Descarta a resposta em cache (útil em testes e após reindexação das estatísticas). */
    public synchronized void invalidarCache() {
        cache = null;
        cacheExpiraEm = 0;
    }

    private PainelUsoRest montar() throws SolrServerException, IOException {
        SolrLoggerService solr = StatisticsServiceFactory.getInstance().getSolrLoggerService();
        int maxItens = configurationService.getIntProperty(PROP_MAX_ITENS, MAX_ITENS_PADRAO);
        int maxTermos = configurationService.getIntProperty(PROP_MAX_TERMOS, MAX_TERMOS_PADRAO);

        PainelUsoRest relatorio = new PainelUsoRest();
        relatorio.setGeradoEm(Instant.now());

        // Visualizacoes de pagina de item, agrupadas pelo uuid do item.
        ObjectCount[] views = solr.queryFacetField(
            "type:" + Constants.ITEM, FQ_VIEW, "id", maxItens, false, null, 1);
        // Downloads: eventos de bitstream agrupados pelo item dono (owningItem),
        // mesma logica de UsageReportUtils#resolveTotalDownloads.
        ObjectCount[] downloads = solr.queryFacetField(
            "type:" + Constants.BITSTREAM, FQ_VIEW, "owningItem", maxItens, false, null, 1);

        Map<String, PainelUsoRest.ItemUso> porItem = new LinkedHashMap<>();
        long totalDocumentos = 0;
        for (ObjectCount oc : views) {
            porItem.computeIfAbsent(oc.getValue(), id -> new PainelUsoRest.ItemUso(id)).setViews(oc.getCount());
            totalDocumentos += oc.getCount();
        }
        long totalDownloads = 0;
        for (ObjectCount oc : downloads) {
            porItem.computeIfAbsent(oc.getValue(), id -> new PainelUsoRest.ItemUso(id)).setDownloads(oc.getCount());
            totalDownloads += oc.getCount();
        }
        relatorio.setPorItem(new ArrayList<>(porItem.values()));

        // "Visualizacoes no site": todo evento de view que nao seja download
        // (itens, colecoes, comunidades e a home). Nao depende de filtros.
        PainelUsoRest.Totais totais = new PainelUsoRest.Totais();
        totais.setSite(solr.queryTotal("*:* -type:" + Constants.BITSTREAM, FQ_VIEW, 0).getCount());
        totais.setDocumentos(totalDocumentos);
        totais.setDownloads(totalDownloads);
        relatorio.setTotais(totais);

        // Termos mais buscados (eventos de busca gravados pelo frontend).
        ObjectCount[] termos = solr.queryFacetField("*:*", FQ_SEARCH, "query", maxTermos, false, null, 1);
        for (ObjectCount oc : termos) {
            String termo = StringUtils.trimToEmpty(oc.getValue());
            if (termo.isEmpty() || "*".equals(termo) || "*:*".equals(termo)) {
                continue;
            }
            relatorio.getTermosBuscados().add(new PainelUsoRest.Termo(termo, oc.getCount()));
        }
        return relatorio;
    }
}
