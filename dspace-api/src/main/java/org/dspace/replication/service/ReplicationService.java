/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.replication.service;

import java.util.UUID;

/**
 * Replicates DSpace items to the external file-ingestion API.
 */
public interface ReplicationService {

    /**
     * Sends the UUID of an item to the external file-ingestion API.
     *
     * @param itemId UUID of the DSpace item to ingest
     */
    void replicateItem(UUID itemId);
}
