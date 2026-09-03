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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.DCDate;
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

public class CustomReviewAction extends ReviewAction {

    private static final Logger log = LogManager.getLogger(CustomReviewAction.class);
    private static final String REASON = "reason";

    @Override
    public List<String> getOptions() {
        List<String> options = new ArrayList<>(super.getOptions());
        options.add("submit_returnForAdjustmentAction");
        return options;
    }

    @Override
    public ActionResult execute(Context c, XmlWorkflowItem wfi, Step step, HttpServletRequest request)
            throws SQLException, AuthorizeException, IOException {
        
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
            
            itemService.addMetadata(c, wfi.getItem(), MetadataSchemaEnum.DC.getName(), 
                                    "description", "provenance", "en", provDescription);
            itemService.update(c, wfi.getItem());

            try {
                Email email = Email.getEmail(I18nUtil.getEmailFilename(c.getCurrentLocale(), "return_for_adjustment"));
                email.addRecipient(submitter.getEmail());
                email.addArgument(wfi.getItem().getName()); 
                email.addArgument(wfi.getItem().getHandle()); 
                email.addArgument(reason); 
                email.send();
            } catch (Exception e) {
                log.error("Erro ao enviar e-mail de devolução para ajuste", e);
            }

            XmlWorkflowServiceFactory.getInstance().getXmlWorkflowService()
                .sendWorkflowItemBackSubmission(c, wfi, currentUser, provDescription, reason);

            return new ActionResult(ActionResult.TYPE.TYPE_SUBMISSION_PAGE);
        }

        return super.execute(c, wfi, step, request);
    }
}