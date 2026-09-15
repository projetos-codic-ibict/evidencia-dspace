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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

/**
 * Preenche automaticamente o metadado {@code local.instituicao} do item com a instituição
 * cadastrada no perfil ({@code eperson.institution}) do usuário que submeteu, quando o item
 * ainda não tiver instituição definida (HU013 CA09). Submissões feitas por administradores são
 * ignoradas, pois costumam ser testes/curadoria, não depósitos de um parceiro real.
 */
public class InstitutionAutoFillConsumer implements Consumer {

    private static final Logger log = LogManager.getLogger(InstitutionAutoFillConsumer.class);

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

                            log.info("AUTOFILL: Submissao detectada. Submitter: {}", submitter.getEmail());

                            ctx.turnOffAuthorisationSystem();

                            try {
                                List<MetadataValue> currentInst = itemService.getMetadata(item, "local", "instituicao", null, Item.ANY);

                                if (currentInst.isEmpty()) {
                                    // eperson.institution é single-value (ver profile-page-metadata-form
                                    // no tema rdapp); get(0) é seguro, não é o bug de "pega o primeiro
                                    // de vários" já achado antes com allMemberGroups().
                                    List<MetadataValue> userInstitutions =
                                        ePersonService.getMetadata(submitter, "eperson", "institution", null, Item.ANY);

                                    if (!userInstitutions.isEmpty()) {
                                        String instValue = userInstitutions.get(0).getValue();

                                        log.info("AUTOFILL: Instituicao capturada do usuario: {}", instValue);

                                        itemService.addMetadata(ctx, item, "local", "instituicao", null, null, instValue);
                                        itemService.update(ctx, item);

                                        log.info("AUTOFILL: Item {} atualizado com a instituicao com SUCESSO!", item.getID());
                                    } else {
                                        log.info("AUTOFILL: Usuario {} nao tem instituicao cadastrada no perfil, item {} ficou sem preenchimento automatico.",
                                            submitter.getEmail(), item.getID());
                                    }
                                } else {
                                    log.info("AUTOFILL: Item {} ja tinha instituicao preenchida, nao sobrescrito.", item.getID());
                                }
                            } catch (Exception e) {
                                log.error("AUTOFILL: Falha ao preencher instituicao do item {}", item.getID(), e);
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
