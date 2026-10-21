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

import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.app.custom.workflow.CustomXmlWorkflowServiceImpl;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.DCDate;
import org.dspace.content.MetadataSchemaEnum;
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
}
