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
import org.junit.Test;

/**
 * Unit tests for {@link ReplicationIndexConsumer}.
 */
public class ReplicationIndexConsumerTest {

    @Test
    public void endDelegatesInstalledItemsToTheReplicationService() throws Exception {
        UUID itemId = UUID.fromString("6c14971d-a4ca-4b5e-a790-e5a73660f80f");
        
        ReplicationService replicationService = mock(ReplicationService.class);
        Context context = mock(Context.class);
        Event event = mock(Event.class);
        Item item = mock(Item.class);
        
        when(event.getSubjectType()).thenReturn(Constants.ITEM);
        when(event.getEventType()).thenReturn(Event.INSTALL);
        when(event.getSubject(context)).thenReturn(item);
        when(item.getID()).thenReturn(itemId);

        ReplicationIndexConsumer consumer = new ReplicationIndexConsumer() {
            @Override
            protected ReplicationService getReplicationService() {
                return replicationService;
            }
        };

        consumer.initialize();
        consumer.consume(context, event);
        consumer.end(context);

        verify(replicationService).replicateItem(itemId);
    }
}
