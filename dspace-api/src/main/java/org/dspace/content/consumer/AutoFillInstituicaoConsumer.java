/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.consumer;

import java.util.List;
import java.util.UUID;

import org.dspace.content.Item;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.GroupService;
import org.dspace.event.Consumer;
import org.dspace.event.Event;

public class AutoFillInstituicaoConsumer implements Consumer {

    private ItemService itemService;
    private GroupService groupService;

    @Override
    public void initialize() throws Exception {
        itemService = ContentServiceFactory.getInstance().getItemService();
        groupService = EPersonServiceFactory.getInstance().getGroupService();
    }

    @Override
    public void consume(Context ctx, Event event) throws Exception {
        if (event.getSubjectType() == Constants.ITEM && event.getEventType() == Event.CREATE) {
            
            UUID itemUuid = event.getSubjectID();
            Item item = itemService.find(ctx, itemUuid);
            
            if (item != null) {
                // A MÁGICA AQUI: Pega o usuário logado diretamente da sessão, 
                // ignorando se o WorkspaceItem já terminou de ser salvo ou não.
                EPerson currentUser = ctx.getCurrentUser();
                
                if (currentUser != null) {
                    List<Group> grupos = groupService.allMemberGroups(ctx, currentUser);
                    String instituicao = null;
                    
                    for (Group grupo : grupos) {
                        String nomeGrupo = grupo.getName();
                        if (nomeGrupo != null 
                            && !nomeGrupo.equals("Administrator") 
                            && !nomeGrupo.equals("Anonymous") 
                            && !nomeGrupo.contains("COLLECTION_") 
                            && !nomeGrupo.contains("COMMUNITY_")) {
                            
                            instituicao = nomeGrupo;
                            break; 
                        }
                    }
                    
                    if (instituicao != null) {
                        // Adiciona o metadado que você confirmou que já existe
                        itemService.addMetadata(ctx, item, "local", "instituicao", null, null, instituicao);
                        itemService.update(ctx, item);
                    }
                }
            }
        }
    }

    @Override
    public void end(Context ctx) throws Exception { }

    @Override
    public void finish(Context ctx) throws Exception { }
}