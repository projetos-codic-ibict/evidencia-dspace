/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import jakarta.servlet.http.HttpServletRequest;
import org.dspace.app.rest.model.PainelUsoRest;
import org.dspace.app.rest.model.RestAddressableModel;
import org.dspace.app.rest.utils.ContextUtil;
import org.dspace.app.rest.utils.PainelUsoUtils;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.core.Context;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de apoio ao Painel de Estatísticas do EvidencIA (HU006).
 *
 * <p>{@code GET /api/statistics/painel/uso}: agregados de visualizações e
 * downloads por item, totais gerais e termos mais buscados, extraídos do core
 * {@code statistics}. Ver {@link PainelUsoRest} para o formato da resposta.</p>
 *
 * <p>Segue a mesma regra de visibilidade dos relatórios nativos de uso: quando
 * {@code usage-statistics.authorization.admin.usage} é {@code true}, só
 * administradores podem consultar; caso contrário o acesso é público.</p>
 */
@RestController
@RequestMapping("/api/" + RestAddressableModel.STATISTICS + "/painel")
public class PainelEstatisticasRestController {

    @Autowired
    private PainelUsoUtils painelUsoUtils;

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private AuthorizeService authorizeService;

    @RequestMapping(method = RequestMethod.GET, value = "/uso", produces = MediaType.APPLICATION_JSON_VALUE)
    public PainelUsoRest getUso(HttpServletRequest request) throws Exception {
        Context context = ContextUtil.obtainContext(request);
        if (configurationService.getBooleanProperty("usage-statistics.authorization.admin.usage", false)
            && !authorizeService.isAdmin(context)) {
            throw new AccessDeniedException("As estatísticas de uso estão restritas a administradores");
        }
        return painelUsoUtils.getPainelUso();
    }
}
