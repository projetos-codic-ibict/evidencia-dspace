package org.dspace.xmlworkflow.state.actions.processingaction;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.DCDate;
import org.dspace.content.Item;
import org.dspace.content.MetadataSchemaEnum;
import org.dspace.core.Context;
import org.dspace.core.Email;
import org.dspace.core.I18nUtil;
import org.dspace.eperson.EPerson;
import org.dspace.xmlworkflow.factory.XmlWorkflowServiceFactory;
import org.dspace.xmlworkflow.state.Step;
import org.dspace.xmlworkflow.state.actions.ActionResult;
import org.dspace.xmlworkflow.storedcomponents.XmlWorkflowItem;

import jakarta.servlet.http.HttpServletRequest;

// ALTERAR: Estender AcceptEditRejectAction permite usar a mesma classe tanto em reviewactionAPI quanto em editactionAPI
public class CustomReviewAction extends AcceptEditRejectAction {

    private static final Logger log = LogManager.getLogger(CustomReviewAction.class);
    private static final String REASON = "reason";

    @Override
    public List<String> getOptions() {
        List<String> options = new ArrayList<>(super.getOptions());
        if (!options.contains("submit_returnForAdjustmentAction")) {
            options.add("submit_returnForAdjustmentAction");
        }
        return options;
    }

    @Override
    public ActionResult execute(Context c, XmlWorkflowItem wfi, Step step, HttpServletRequest request)
            throws SQLException, AuthorizeException, IOException {

        Item item = wfi.getItem();

        // CASO 1: DEVOLVER PARA AJUSTE
        if (request.getParameter("submit_returnForAdjustmentAction") != null) {
            String reason = request.getParameter(REASON);
            if (reason == null || reason.trim().isEmpty()) {
                return new ActionResult(ActionResult.TYPE.TYPE_ERROR);
            }

            EPerson submitter = wfi.getSubmitter();
            EPerson currentUser = c.getCurrentUser();

            String provDescription = "Devolvido para ajuste por " + currentUser.getFullName() +
                                     " (" + currentUser.getEmail() + ") em " + DCDate.getCurrent() +
                                     " com o seguinte motivo: " + reason;

            // ADICIONAR: Limpa status anteriores e grava em dc.description.abstract ou dc.description (visível para o depositante via REST)
            // Usamos um prefixo identificador "[DEVOLVIDO_AJUSTE]" em dc.description para o Angular ler mesmo sem ser Admin
            removePreviousWorkflowNotes(c, item);
            itemService.addMetadata(c, item, MetadataSchemaEnum.DC.getName(),
                                    "description", null, "pt_BR",
                                    "[DEVOLVIDO_AJUSTE] " + reason);

            // Mantém o registro oficial no provenance para auditoria
            itemService.addMetadata(c, item, MetadataSchemaEnum.DC.getName(),
                                    "description", "provenance", "pt_BR", provDescription);
            itemService.update(c, item);

            try {
                Email email = Email.getEmail(I18nUtil.getEmailFilename(c.getCurrentLocale(), "return_for_adjustment"));
                email.addRecipient(submitter.getEmail());
                email.addArgument(item.getName());
                email.addArgument(item.getHandle() != null ? item.getHandle() : "");
                email.addArgument(reason);
                email.send();
            } catch (Exception e) {
                log.error("Erro ao enviar e-mail de devolução para ajuste", e);
            }

            // Devolve o item para o Workspace do depositante
            XmlWorkflowServiceFactory.getInstance().getXmlWorkflowService()
                .sendWorkflowItemBackSubmission(c, wfi, currentUser, provDescription, reason);

            return new ActionResult(ActionResult.TYPE.TYPE_SUBMISSION_PAGE);
        }

        // CASO 2: REJEITAR PADRÃO (Garante que o motivo da rejeição também fique visível para o depositante em D1)
        if (request.getParameter("submit_reject") != null) {
            String reason = request.getParameter(REASON);
            if (reason != null && !reason.trim().isEmpty()) {
                removePreviousWorkflowNotes(c, item);
                itemService.addMetadata(c, item, MetadataSchemaEnum.DC.getName(),
                                        "description", null, "pt_BR",
                                        "[REJEITADO] " + reason);
                itemService.update(c, item);
            }
        }

        return super.execute(c, wfi, step, request);
    }

    private void removePreviousWorkflowNotes(Context c, Item item) throws SQLException {
        List<org.dspace.content.MetadataValue> descriptions = itemService.getMetadata(item, "dc", "description", null, Item.ANY);
        List<String> toKeep = new ArrayList<>();
        for (org.dspace.content.MetadataValue mv : descriptions) {
            if (mv.getValue() != null && !mv.getValue().startsWith("[DEVOLVIDO_AJUSTE]") && !mv.getValue().startsWith("[REJEITADO]")) {
                toKeep.add(mv.getValue());
            }
        }
        itemService.clearMetadata(c, item, "dc", "description", null, Item.ANY);
        for (String val : toKeep) {
            itemService.addMetadata(c, item, "dc", "description", null, "pt_BR", val);
        }
    }
}