/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.replication;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.event.Consumer;
import org.dspace.event.Event;
import org.dspace.replication.service.ReplicationService;
import org.dspace.services.ConfigurationService;
import org.dspace.utils.DSpace;

public class ReplicationIndexConsumer implements Consumer {

    private static final int SUPPORTED_EVENTS = Event.INSTALL;
    private static final String CONSUMER_ENABLED_PROPERTY = "replication.consumer.enabled";
    private static final String TEST_ASYNC_DELAY_PROPERTY = "replication.consumer.test-async-delay-ms";

    private Set<UUID> itemsToReplicate;
    private ReplicationService replicationService;
    private boolean enabled;
    private long testAsyncDelayMs;

    @Override
    public void initialize() throws Exception {
        enabled = getConfigurationService().getBooleanProperty(CONSUMER_ENABLED_PROPERTY, true);
        if (!enabled) {
            return;
        }

        itemsToReplicate = new HashSet<>();
        replicationService = getReplicationService();
        testAsyncDelayMs = Math.max(0,
                                    getConfigurationService().getLongProperty(TEST_ASYNC_DELAY_PROPERTY, 0));
    }

    @Override
    public void consume(Context ctx, Event event) throws Exception {
        if (!enabled) {
            return;
        }

        boolean isEventValid =
            event.getSubjectType() == Constants.ITEM && (event.getEventType() & SUPPORTED_EVENTS) != 0;

        if (!isEventValid) {
            return;
        }

        DSpaceObject subject = event.getSubject(ctx);
        if (subject instanceof Item) {
            itemsToReplicate.add(subject.getID());
        }
    }

    @Override
    public void end(Context ctx) throws Exception {
        if (!enabled) {
            return;
        }

        if (itemsToReplicate.isEmpty()) {
            return;
        }

        for (UUID itemId : itemsToReplicate) {
            if (testAsyncDelayMs > 0) 
            {
                scheduleReplicationForTest(itemId, testAsyncDelayMs);
            } 
            else {
                replicationService.replicateItem(itemId);
            }
        }

        itemsToReplicate.clear();
    }

    @Override
    public void finish(Context ctx) throws Exception {
    }

    /**
     * Gets the service responsible for replicating items to the external API.
     *
     * @return item replication service
     */
    protected ReplicationService getReplicationService() {
        return new DSpace().getSingletonService(ReplicationService.class);
    }

    /**
     * Schedules a delayed asynchronous replication.
     *
     * <p>It must remain disabled in normal operation: it does not provide transactional delivery guarantees.
     * It merely lets the current transaction complete before the external API is called.</p>
     *
     * @param itemId item UUID to replicate
     * @param delayMs delay in milliseconds
     */
    protected void scheduleReplicationForTest(UUID itemId, long delayMs) {
        CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS)
                         .execute(() -> replicationService.replicateItem(itemId));
    }

    /**
     * Gets the configuration service used to enable or disable this consumer.
     *
     * @return the DSpace configuration service
     */
    protected ConfigurationService getConfigurationService() {
        return new DSpace().getConfigurationService();
    }
}
