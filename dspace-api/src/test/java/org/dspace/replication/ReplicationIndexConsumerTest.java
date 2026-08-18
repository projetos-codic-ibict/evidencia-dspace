/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.replication;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.dspace.content.Item;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.event.Event;
import org.dspace.replication.service.ReplicationService;
import org.dspace.services.ConfigurationService;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link ReplicationIndexConsumer}.
 */
public class ReplicationIndexConsumerTest {

    @Test
    public void endDelegatesInstalledItemsToTheReplicationService() throws Exception {
        UUID itemId = UUID.fromString("6c14971d-a4ca-4b5e-a790-e5a73660f80f");
        
        ReplicationService replicationService = mock(ReplicationService.class);
        ConfigurationService configurationService = mock(ConfigurationService.class);
        Context context = mock(Context.class);
        Event event = mock(Event.class);
        Item item = mock(Item.class);
        
        when(event.getSubjectType()).thenReturn(Constants.ITEM);
        when(event.getEventType()).thenReturn(Event.INSTALL);
        when(event.getSubject(context)).thenReturn(item);
        when(item.getID()).thenReturn(itemId);
        when(configurationService.getBooleanProperty("replication.consumer.enabled", true)).thenReturn(true);
        when(configurationService.getLongProperty("replication.consumer.test-async-delay-ms", 0)).thenReturn(0L);

        ReplicationIndexConsumer consumer = new ReplicationIndexConsumer() {
            @Override
            protected ReplicationService getReplicationService() {
                return replicationService;
            }

            @Override
            protected ConfigurationService getConfigurationService() {
                return configurationService;
            }
        };

        consumer.initialize();
        consumer.consume(context, event);
        consumer.end(context);

        verify(replicationService).replicateItem(itemId);
    }

    @Test
    public void endSchedulesReplicationWhenDiagnosticDelayIsEnabled() throws Exception {
        UUID itemId = UUID.fromString("6c14971d-a4ca-4b5e-a790-e5a73660f80f");
        long delayMs = 5000L;

        ReplicationService replicationService = mock(ReplicationService.class);
        ConfigurationService configurationService = mock(ConfigurationService.class);
        Context context = mock(Context.class);
        Event event = mock(Event.class);
        Item item = mock(Item.class);
        UUID[] scheduledItemId = new UUID[1];
        long[] scheduledDelayMs = new long[1];

        when(event.getSubjectType()).thenReturn(Constants.ITEM);
        when(event.getEventType()).thenReturn(Event.INSTALL);
        when(event.getSubject(context)).thenReturn(item);
        when(item.getID()).thenReturn(itemId);
        when(configurationService.getBooleanProperty("replication.consumer.enabled", true)).thenReturn(true);
        when(configurationService.getLongProperty("replication.consumer.test-async-delay-ms", 0))
            .thenReturn(delayMs);

        ReplicationIndexConsumer consumer = new ReplicationIndexConsumer() {
            @Override
            protected ReplicationService getReplicationService() {
                return replicationService;
            }

            @Override
            protected ConfigurationService getConfigurationService() {
                return configurationService;
            }

            @Override
            protected void scheduleReplicationForTest(UUID id, long delay) {
                scheduledItemId[0] = id;
                scheduledDelayMs[0] = delay;
            }
        };

        consumer.initialize();
        consumer.consume(context, event);
        consumer.end(context);

        Assert.assertEquals(itemId, scheduledItemId[0]);
        Assert.assertEquals(delayMs, scheduledDelayMs[0]);
    }

    @Test
    public void disabledConsumerDoesNotReplicateInstalledItems() throws Exception {
        ConfigurationService configurationService = mock(ConfigurationService.class);
        Context context = mock(Context.class);
        Event event = mock(Event.class);

        when(configurationService.getBooleanProperty("replication.consumer.enabled", true)).thenReturn(false);

        ReplicationIndexConsumer consumer = new ReplicationIndexConsumer() {
            @Override
            protected ConfigurationService getConfigurationService() {
                return configurationService;
            }

            @Override
            protected ReplicationService getReplicationService() {
                throw new AssertionError("The replication service must not be initialized when disabled");
            }
        };

        consumer.initialize();
        consumer.consume(context, event);
        consumer.end(context);
    }
}
