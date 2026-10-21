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
import org.dspace.authorize.AuthorizeException;
import org.dspace.core.Context;
import org.dspace.xmlworkflow.state.Step;
import org.dspace.xmlworkflow.state.actions.ActionResult;
import org.dspace.xmlworkflow.storedcomponents.XmlWorkflowItem;

/**
 * Ação do curador: aprovar, rejeitar, editar, retornar ao conjunto e devolver para ajuste. Estende
 * AcceptEditRejectAction, então serve tanto para o passo Editores (editactionAPI) quanto para itens que ainda estão
 * no passo Revisores (reviewactionAPI). O motivo da rejeição e da devolução fica numa anotação pública em
 * dc.description, que o depositante lê no MyDSpace, e a anotação sai quando o item é aprovado.
 */
public class CustomReviewAction extends AcceptEditRejectAction {

    private static final String REASON = "reason";

    private static final String SUBMIT_REJECT_PARAM = "submit_reject";

    @Override
    public List<String> getOptions() {
        List<String> options = new ArrayList<>(super.getOptions());
        if (!options.contains(ReturnForAdjustmentSupport.OPTION)) {
            options.add(ReturnForAdjustmentSupport.OPTION);
        }
        return options;
    }

    @Override
    public ActionResult execute(Context c, XmlWorkflowItem wfi, Step step, HttpServletRequest request)
        throws SQLException, AuthorizeException, IOException {
        if (request.getParameter(ReturnForAdjustmentSupport.OPTION) != null) {
            return ReturnForAdjustmentSupport.returnForAdjustment(c, wfi, request, itemService);
        }

        if (request.getParameter(SUBMIT_REJECT_PARAM) != null) {
            String reason = request.getParameter(REASON);
            if (reason != null && !reason.trim().isEmpty()) {
                ReturnForAdjustmentSupport.replaceNote(c, wfi.getItem(), itemService,
                                                       ReturnForAdjustmentSupport.NOTE_REJECTED, reason);
                itemService.update(c, wfi.getItem());
            }
        }
        return super.execute(c, wfi, step, request);
    }

    @Override
    public ActionResult processAccept(Context c, XmlWorkflowItem wfi) throws SQLException, AuthorizeException {
        ReturnForAdjustmentSupport.clearNotes(c, wfi.getItem(), itemService);
        itemService.update(c, wfi.getItem());
        return super.processAccept(c, wfi);
    }
}
