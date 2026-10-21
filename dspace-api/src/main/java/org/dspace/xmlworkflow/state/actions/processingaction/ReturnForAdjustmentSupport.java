/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.xmlworkflow.state.actions.processingaction;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.app.custom.workflow.CustomXmlWorkflowServiceImpl;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.DCDate;
import org.dspace.content.Item;
import org.dspace.content.MetadataSchemaEnum;
import org.dspace.content.MetadataValue;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.core.Email;
import org.dspace.core.I18nUtil;
import org.dspace.eperson.EPerson;
import org.dspace.xmlworkflow.factory.XmlWorkflowServiceFactory;
import org.dspace.xmlworkflow.state.actions.ActionResult;
import org.dspace.xmlworkflow.storedcomponents.XmlWorkflowItem;

/**
 * Lógica de "Devolver para ajuste", compartilhada pelas ações do workflow que oferecem essa opção
 * (hoje {@link CustomReviewAction} e {@link CustomAcceptEditRejectAction}).
 */
final class ReturnForAdjustmentSupport {

    static final String OPTION = "submit_returnForAdjustmentAction";

    /** Prefixos da anotação pública em dc.description que o depositante lê no card do MyDSpace. */
    static final String NOTE_ADJUSTMENT = "[DEVOLVIDO_AJUSTE] ";

    static final String NOTE_REJECTED = "[REJEITADO] ";

    private static final Logger log = LogManager.getLogger(ReturnForAdjustmentSupport.class);

    private static final String REASON = "reason";

    private ReturnForAdjustmentSupport() {
    }

    /**
     * Devolve o item ao depositante sem caracterizar rejeição.
     *
     * @return erro se o motivo estiver vazio, ou volta para a página de submissões
     */
    static ActionResult returnForAdjustment(Context c, XmlWorkflowItem wfi, HttpServletRequest request,
                                            ItemService itemService)
        throws SQLException, AuthorizeException, IOException {
        String reason = request.getParameter(REASON);
        if (reason == null || reason.trim().isEmpty()) {
            return new ActionResult(ActionResult.TYPE.TYPE_ERROR);
        }

        EPerson submitter = wfi.getSubmitter();
        EPerson currentUser = c.getCurrentUser();

        String provDescription = "Devolvido para ajuste por " + currentUser.getFullName() +
                                 " (" + currentUser.getEmail() + ") em " + DCDate.getCurrent() +
                                 " com o seguinte motivo: " + reason;

        // A proveniência só é legível para administradores. O depositante lê o motivo na anotação pública.
        replaceNote(c, wfi.getItem(), itemService, NOTE_ADJUSTMENT, reason);
        itemService.addMetadata(c, wfi.getItem(), MetadataSchemaEnum.DC.getName(),
                                "description", "provenance", "en", provDescription);
        itemService.update(c, wfi.getItem());

        try {
            Email email = Email.getEmail(
                I18nUtil.getEmailFilename(I18nUtil.getEPersonLocale(submitter), "return_for_adjustment"));
            email.addRecipient(submitter.getEmail());
            email.addArgument(wfi.getItem().getName());
            email.addArgument(wfi.getItem().getHandle());
            email.addArgument(reason);
            email.send();
        } catch (Exception e) {
            log.error("Erro ao enviar e-mail de devolução para ajuste", e);
        }

        // Usa o serviço de workflow customizado (não o sendWorkflowItemBackSubmission nativo)
        // pra devolver o item sem caracterizar rejeição: sem "Rejected by" na proveniência e
        // sem o e-mail nativo de rejeição, que o nativo dispararia por dentro.
        ((CustomXmlWorkflowServiceImpl) XmlWorkflowServiceFactory.getInstance().getXmlWorkflowService())
            .sendWorkflowItemBackForAdjustment(c, wfi, currentUser);

        return new ActionResult(ActionResult.TYPE.TYPE_SUBMISSION_PAGE);
    }

    /**
     * Troca a anotação de workflow em dc.description (devolução ou rejeição anterior) pela nova.
     */
    static void replaceNote(Context c, Item item, ItemService itemService, String prefix, String reason)
        throws SQLException {
        clearNotes(c, item, itemService);
        itemService.addMetadata(c, item, MetadataSchemaEnum.DC.getName(), "description", null, "pt_BR",
                                prefix + reason);
    }

    /**
     * Remove as anotações de workflow de dc.description e mantém os demais valores. Chamado também na aprovação,
     * para a anotação não ficar no metadado público do item arquivado.
     */
    static void clearNotes(Context c, Item item, ItemService itemService) throws SQLException {
        List<MetadataValue> descriptions = itemService.getMetadata(item, MetadataSchemaEnum.DC.getName(),
                                                                   "description", null, Item.ANY);
        List<String> keep = new ArrayList<>();
        boolean hadNote = false;
        for (MetadataValue value : descriptions) {
            String text = value.getValue();
            if (text != null && (text.startsWith(NOTE_ADJUSTMENT) || text.startsWith(NOTE_REJECTED))) {
                hadNote = true;
            } else {
                keep.add(text);
            }
        }
        if (!hadNote) {
            return;
        }
        itemService.clearMetadata(c, item, MetadataSchemaEnum.DC.getName(), "description", null, Item.ANY);
        for (String text : keep) {
            itemService.addMetadata(c, item, MetadataSchemaEnum.DC.getName(), "description", null, "pt_BR", text);
        }
    }
}
