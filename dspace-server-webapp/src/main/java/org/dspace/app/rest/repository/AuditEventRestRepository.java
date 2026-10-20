/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.repository;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.dspace.app.audit.AuditEvent;
import org.dspace.app.audit.AuditSolrServiceImpl;
import org.dspace.app.rest.Parameter;
import org.dspace.app.rest.SearchRestMethod;
import org.dspace.app.rest.converter.ConverterService;
import org.dspace.app.rest.model.AuditEventRest;
import org.dspace.authorize.AuthorizeException;
import org.dspace.core.Context;
import org.dspace.services.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

/**
 * This is the repository responsible to manage Audit Event Rest object
 *
 * @author Andrea Bollini (andrea.bollini at 4science.it)
 */
@Component(AuditEventRest.CATEGORY + "." + AuditEventRest.NAME_PLURAL)
public class AuditEventRestRepository extends DSpaceRestRepository<AuditEventRest, UUID> {

    private static final Logger log = LoggerFactory.getLogger(AuditEventRestRepository.class);

    @Autowired
    private AuditSolrServiceImpl auditSolrService;

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    protected ConverterService converter;

    @Override
    @PreAuthorize("hasAuthority('ADMIN')")
    public AuditEventRest findOne(Context context, UUID id) {
        returnNotFoundIfDisabled();
        AuditEvent audit = auditSolrService.findEvent(context, id);
        return converter.toRest(audit, utils.obtainProjection());
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @SearchRestMethod(name = "findByObject")
    public Page<AuditEventRest> findByObject(@Parameter(value = "object", required = true) UUID uuid,
            Pageable pageable) throws AuthorizeException, SQLException {
        returnNotFoundIfDisabled();
        Context context = obtainContext();
        Sort sort = pageable.getSort();
        boolean asc = sort.isUnsorted() || (sort.isSorted() && sort.getOrderFor("timeStamp") != null
                && sort.getOrderFor("timeStamp").isAscending());
        List<AuditEvent> events = auditSolrService.findEvents(uuid, null, null, pageable.getPageSize(),
                (int) pageable.getOffset(), asc);
        long total = auditSolrService.countEvents(context, uuid, null, null);
        return converter.toRestPage(events, pageable, total, utils.obtainProjection());
    }

    /**
     * Search audit events by subject type (RDAPP: usado pra focar a auditoria em EPerson/Group).
     *
     * @param types    comma-separated list of subject types (e.g. "EPerson,Group")
     * @param pageable pagination info
     * @return the matching audit events
     */
    @PreAuthorize("hasAuthority('ADMIN')")
    @SearchRestMethod(name = "findBySubjectType")
    public Page<AuditEventRest> findBySubjectType(@Parameter(value = "types", required = true) String types,
            Pageable pageable) {
        returnNotFoundIfDisabled();
        Context context = obtainContext();
        Sort sort = pageable.getSort();
        boolean asc = sort.isUnsorted() || (sort.isSorted() && sort.getOrderFor("timeStamp") != null
                && sort.getOrderFor("timeStamp").isAscending());
        String[] subjectTypes = types.split(",");
        List<AuditEvent> events = auditSolrService.findEventsBySubjectType(subjectTypes, pageable.getPageSize(),
                (int) pageable.getOffset(), asc);
        long total = auditSolrService.countEventsBySubjectType(subjectTypes);
        return converter.toRestPage(events, pageable, total, utils.obtainProjection());
    }

    /**
     * D9 / CA09.3: Busca combinada de eventos de auditoria com múltiplos filtros opcionais.
     * Endpoint: /api/system/auditevents/search/findByFilters
     *
     * @param types       tipos de sujeito separados por vírgula (ex: "EPerson,Group")
     * @param eventType   tipo de ação/evento (ex: "CREATE", "MODIFY", "POLICY_CREATE")
     * @param object      UUID do objeto ou sujeito relacionado
     * @param eperson     UUID do usuário (EPerson) que executou a ação
     * @param dateFrom    data inicial no formato YYYY-MM-DD
     * @param dateTo      data final no formato YYYY-MM-DD
     * @param pageable    paginação e ordenação
     */
    @PreAuthorize("hasAuthority('ADMIN')")
    @SearchRestMethod(name = "findByFilters")
    public Page<AuditEventRest> findByFilters(
            @Parameter(value = "types", required = false) String types,
            @Parameter(value = "eventType", required = false) String eventType,
            @Parameter(value = "object", required = false) UUID object,
            @Parameter(value = "eperson", required = false) UUID eperson,
            @Parameter(value = "dateFrom", required = false) String dateFrom,
            @Parameter(value = "dateTo", required = false) String dateTo,
            Pageable pageable) {
        returnNotFoundIfDisabled();
        Sort sort = pageable.getSort();
        // Por padrão nas trilhas de auditoria, se não houver sort explícito ascendente, ordena pelos mais recentes (desc)
        boolean asc = sort.isSorted() && sort.getOrderFor("timeStamp") != null
                && sort.getOrderFor("timeStamp").isAscending();

        String[] subjectTypes = StringUtils.isNotBlank(types) ? types.split(",") : null;

        List<AuditEvent> events = auditSolrService.findEventsByFilters(
                subjectTypes, eventType, object, eperson, dateFrom, dateTo,
                pageable.getPageSize(), (int) pageable.getOffset(), asc);

        long total = auditSolrService.countEventsByFilters(
                subjectTypes, eventType, object, eperson, dateFrom, dateTo);

        return converter.toRestPage(events, pageable, total, utils.obtainProjection());
    }

    @Override
    public Class<AuditEventRest> getDomainClass() {
        return AuditEventRest.class;
    }

    @Override
    @PreAuthorize("hasAuthority('ADMIN')")
    public Page<AuditEventRest> findAll(Context context, Pageable pageable) {
        returnNotFoundIfDisabled();
        Sort sort = pageable.getSort();
        boolean asc = sort.isUnsorted() || (sort.isSorted() && sort.getOrderFor("timeStamp") != null
                && sort.getOrderFor("timeStamp").isAscending());
        List<AuditEvent> events = auditSolrService.findAllEvents(context, pageable.getPageSize(),
                (int) pageable.getOffset(), asc);
        long total = auditSolrService.countAllEvents(context);
        return converter.toRestPage(events, pageable, total, utils.obtainProjection());
    }

    private void returnNotFoundIfDisabled() {
        if (!configurationService.getBooleanProperty("audit.enabled")) {
            throw new ResourceNotFoundException("Audit service is disabled");
        }
    }
}