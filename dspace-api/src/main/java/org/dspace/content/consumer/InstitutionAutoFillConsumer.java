/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.consumer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.dspace.authorize.factory.AuthorizeServiceFactory;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.WorkspaceItem;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.WorkspaceItemService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.EPersonService;
import org.dspace.event.Consumer;
import org.dspace.event.Event;

public class InstitutionAutoFillConsumer implements Consumer {

    private ItemService itemService;
    private EPersonService ePersonService;
    private WorkspaceItemService wsiService;
    private AuthorizeService authorizeService;

    private Set<UUID> itemsToProcess = null;

    @Override
    public void initialize() throws Exception {
        itemService = ContentServiceFactory.getInstance().getItemService();
        ePersonService = EPersonServiceFactory.getInstance().getEPersonService();
        wsiService = ContentServiceFactory.getInstance().getWorkspaceItemService();
        authorizeService = AuthorizeServiceFactory.getInstance().getAuthorizeService();
    }

    @Override
    public void consume(Context ctx, Event event) throws Exception {
        if (itemsToProcess == null) {
            itemsToProcess = new HashSet<>();
        }
        if (event.getSubjectType() == Constants.ITEM) {
            itemsToProcess.add(event.getSubjectID());
        }
    }

    @Override
    public void end(Context ctx) throws Exception {
        if (itemsToProcess != null && !itemsToProcess.isEmpty()) {

            for (UUID itemId : itemsToProcess) {
                Item item = itemService.find(ctx, itemId);

                if (item != null) {
                    WorkspaceItem wsi = wsiService.findByItem(ctx, item);

                    if (wsi != null) {
                        EPerson submitter = wsi.getSubmitter();

                        if (submitter != null) {

                            if (authorizeService.isAdmin(ctx, submitter)) {
                                continue;
                            }

                            ctx.turnOffAuthorisationSystem();

                            try {
                                List<MetadataValue> currentInst = itemService.getMetadata(item, "local", "instituicao", null, Item.ANY);

                                if (currentInst.isEmpty()) {
                                    List<MetadataValue> userInstitutions = ePersonService.getMetadata(submitter, "eperson", "institution", null, Item.ANY);

                                    if (!userInstitutions.isEmpty()) {
                                        String instValue = userInstitutions.get(0).getValue();

                                        itemService.addMetadata(ctx, item, "local", "instituicao", null, null, instValue);
                                        itemService.update(ctx, item);
                                    } else {
                                    }
                                } else {
                                }
                            } catch (Exception e) {
                            } finally {
                                ctx.restoreAuthSystemState();
                            }
                        }
                    }
                }
            }
            itemsToProcess.clear();
        }
    }

    @Override
    public void finish(Context ctx) throws Exception {
        itemsToProcess = null;
    }
}
