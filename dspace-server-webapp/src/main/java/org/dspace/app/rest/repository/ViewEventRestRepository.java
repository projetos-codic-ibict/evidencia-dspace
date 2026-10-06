/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.repository;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.dspace.app.rest.exception.DSpaceBadRequestException;
import org.dspace.app.rest.exception.TooManyRequestsException;
import org.dspace.app.rest.exception.UnprocessableEntityException;
import org.dspace.app.rest.model.ViewEventRest;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.DSpaceObject;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.DSpaceObjectService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.service.ClientInfoService;
import org.dspace.services.EventService;
import org.dspace.usage.UsageEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component(ViewEventRest.CATEGORY + "." + ViewEventRest.PLURAL_NAME)
public class ViewEventRestRepository extends AbstractDSpaceRestRepository {

    /** Usage event type used to log a "user copied the item's citation/reference" event. */
    private static final String EVENT_TYPE_REFERENCE_COPY = "reference_copy";

    /** Minimum interval between accepted reference-copy events from the same client for the same target. */
    private static final long REFERENCE_COPY_MIN_INTERVAL_SECONDS = 3;

    @Autowired
    private EventService eventService;

    @Autowired
    private ObjectMapper mapper;

    private final List<String> typeList = Arrays.asList(Constants.typeText);

    /** Above this many entries, stale ones are purged so the map does not grow forever. */
    private static final int REFERENCE_COPY_MAX_TRACKED_KEYS = 10000;

    @Autowired
    private ClientInfoService clientInfoService;

    /** Last accepted reference-copy event time, keyed by "clientIp:targetId". */
    private final ConcurrentHashMap<String, Instant> lastReferenceCopyByClientAndTarget = new ConcurrentHashMap<>();

    public ViewEventRest createViewEvent() throws AuthorizeException, SQLException {

        Context context = obtainContext();
        HttpServletRequest req = getRequestService().getCurrentRequest().getHttpServletRequest();
        ViewEventRest viewEventRest = null;
        try {
            ServletInputStream input = req.getInputStream();
            viewEventRest = mapper.readValue(input, ViewEventRest.class);
        } catch (IOException e1) {
            throw new UnprocessableEntityException("Error parsing request body", e1);
        }
        if (viewEventRest.getTargetId() == null || StringUtils.isBlank(viewEventRest.getTargetType()) ||
            !typeList.contains(viewEventRest.getTargetType().toUpperCase())) {
            throw new DSpaceBadRequestException("The given ViewEvent was invalid, one or more properties are either" +
                                                    " wrong or missing");
        }
        DSpaceObjectService dSpaceObjectService = ContentServiceFactory.getInstance().getDSpaceObjectService(
            Constants.getTypeID(viewEventRest.getTargetType().toUpperCase(Locale.getDefault())));

        DSpaceObject dSpaceObject = dSpaceObjectService.find(context, viewEventRest.getTargetId());
        if (dSpaceObject == null) {
            throw new UnprocessableEntityException(
                "The given targetId does not resolve to a DSpaceObject: " + viewEventRest.getTargetId());
        }

        UsageEvent.Action action = UsageEvent.Action.VIEW;
        if (EVENT_TYPE_REFERENCE_COPY.equalsIgnoreCase(viewEventRest.getEventType())) {
            checkReferenceCopyRateLimit(req, viewEventRest.getTargetId().toString());
            action = UsageEvent.Action.REFERENCE_COPY;
        }

        UsageEvent usageEvent = new UsageEvent(action, req, context, dSpaceObject,
                viewEventRest.getReferrer());
        eventService.fireEvent(usageEvent);
        return viewEventRest;
    }

    /**
     * Rejects reference-copy events sent faster than {@link #REFERENCE_COPY_MIN_INTERVAL_SECONDS} by the same
     * client IP for the same target, to avoid a repeated/scripted click inflating the count.
     */
    private void checkReferenceCopyRateLimit(HttpServletRequest req, String targetId) {
        // Mesmo IP que o logger do Solr usa (respeita X-Forwarded-For), senão atrás de proxy todos dividem a chave
        String key = clientInfoService.getClientIp(req) + ":" + targetId;
        Instant now = Instant.now();
        if (lastReferenceCopyByClientAndTarget.size() > REFERENCE_COPY_MAX_TRACKED_KEYS) {
            Instant cutoff = now.minusSeconds(REFERENCE_COPY_MIN_INTERVAL_SECONDS);
            lastReferenceCopyByClientAndTarget.values().removeIf(lastAccepted -> lastAccepted.isBefore(cutoff));
        }
        boolean[] tooSoon = {false};
        // O horário só é regravado quando o evento é aceito, para cliques repetidos não prolongarem o bloqueio
        lastReferenceCopyByClientAndTarget.compute(key, (k, previous) -> {
            if (previous != null && now.minusSeconds(REFERENCE_COPY_MIN_INTERVAL_SECONDS).isBefore(previous)) {
                tooSoon[0] = true;
                return previous;
            }
            return now;
        });
        if (tooSoon[0]) {
            throw new TooManyRequestsException(
                "Too many reference-copy events for target " + targetId + ", please wait a few seconds");
        }
    }
}
