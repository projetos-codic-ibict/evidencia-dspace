/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Resposta do endpoint {@code GET /api/statistics/painel/uso}, consumido pela aba
 * "Total de Visualizações" do Painel de Estatísticas (HU006).
 *
 * <p>Entrega os agregados de uso extraídos do core {@code statistics} do Solr:
 * totais gerais, contagem de visualizações e downloads POR ITEM (para o
 * frontend cruzar com os metadados vindos do Discovery, respeitando os filtros
 * da barra lateral) e os termos de busca mais frequentes (nuvem de palavras da
 * aba). Objeto simples serializado pelo Jackson; não é um recurso HAL.</p>
 */
public class PainelUsoRest {

    private Totais totais = new Totais();
    private List<ItemUso> porItem = new ArrayList<>();
    private List<Termo> termosBuscados = new ArrayList<>();
    private Instant geradoEm;
    private int cacheSegundos;

    public Totais getTotais() {
        return totais;
    }

    public void setTotais(Totais totais) {
        this.totais = totais;
    }

    public List<ItemUso> getPorItem() {
        return porItem;
    }

    public void setPorItem(List<ItemUso> porItem) {
        this.porItem = porItem;
    }

    public List<Termo> getTermosBuscados() {
        return termosBuscados;
    }

    public void setTermosBuscados(List<Termo> termosBuscados) {
        this.termosBuscados = termosBuscados;
    }

    public Instant getGeradoEm() {
        return geradoEm;
    }

    public void setGeradoEm(Instant geradoEm) {
        this.geradoEm = geradoEm;
    }

    public int getCacheSegundos() {
        return cacheSegundos;
    }

    public void setCacheSegundos(int cacheSegundos) {
        this.cacheSegundos = cacheSegundos;
    }

    /**
     * Totais gerais do repositório (não dependem dos filtros do painel).
     * <ul>
     *   <li>{@code site}: visualizações de páginas (itens, coleções, comunidades e a
     *       própria home), isto é, todo evento de view exceto os de bitstream;</li>
     *   <li>{@code documentos}: visualizações de páginas de item;</li>
     *   <li>{@code downloads}: acessos a bitstreams do bundle configurado
     *       (por padrão ORIGINAL), a mesma regra de
     *       {@code UsageReportUtils#resolveTotalDownloads}.</li>
     * </ul>
     */
    public static class Totais {
        private long site;
        private long documentos;
        private long downloads;

        public long getSite() {
            return site;
        }

        public void setSite(long site) {
            this.site = site;
        }

        public long getDocumentos() {
            return documentos;
        }

        public void setDocumentos(long documentos) {
            this.documentos = documentos;
        }

        public long getDownloads() {
            return downloads;
        }

        public void setDownloads(long downloads) {
            this.downloads = downloads;
        }
    }

    /** Uso de um item: uuid, visualizações da página e downloads dos seus bitstreams. */
    public static class ItemUso {
        private String id;
        private long views;
        private long downloads;

        public ItemUso() {
        }

        public ItemUso(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public long getViews() {
            return views;
        }

        public void setViews(long views) {
            this.views = views;
        }

        public long getDownloads() {
            return downloads;
        }

        public void setDownloads(long downloads) {
            this.downloads = downloads;
        }
    }

    /** Termo de busca e quantidade de vezes que foi pesquisado. */
    public static class Termo {
        private String label;
        private long count;

        public Termo() {
        }

        public Termo(String label, long count) {
            this.label = label;
            this.count = count;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public long getCount() {
            return count;
        }

        public void setCount(long count) {
            this.count = count;
        }
    }
}
