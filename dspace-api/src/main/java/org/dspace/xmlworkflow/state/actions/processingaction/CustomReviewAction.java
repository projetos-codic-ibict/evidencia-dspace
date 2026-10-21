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

public class CustomReviewAction extends ReviewAction {

    @Override
    public List<String> getOptions() {
        List<String> options = new ArrayList<>(super.getOptions());
        options.add(ReturnForAdjustmentSupport.OPTION);
        return options;
    }

    @Override
    public ActionResult execute(Context c, XmlWorkflowItem wfi, Step step, HttpServletRequest request)
            throws SQLException, AuthorizeException, IOException {
        if (request.getParameter(ReturnForAdjustmentSupport.OPTION) != null) {
            return ReturnForAdjustmentSupport.returnForAdjustment(c, wfi, request, itemService);
        }
        return super.execute(c, wfi, step, request);
    }
}
