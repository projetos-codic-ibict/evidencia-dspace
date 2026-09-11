/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.custom.workflow;

import java.io.IOException;
import java.sql.SQLException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Item;
import org.dspace.content.WorkspaceItem;
import org.dspace.core.Context;
import org.dspace.core.LogHelper;
import org.dspace.eperson.EPerson;
import org.dspace.xmlworkflow.XmlWorkflowServiceImpl;
import org.dspace.xmlworkflow.service.WorkflowRequirementsService;
import org.dspace.xmlworkflow.storedcomponents.ClaimedTask;
import org.dspace.xmlworkflow.storedcomponents.XmlWorkflowItem;

/**
 * Extensão do serviço de workflow nativo pra suportar a ação "Devolver para ajuste" (HU010 CA04 /
 * HU012 CA06/CA09) sem que ela seja tratada como rejeição por dentro.
 *
 * O método nativo {@link #sendWorkflowItemBackSubmission} grava uma segunda proveniência com
 * "Rejected by" e dispara o e-mail nativo de rejeição (notifyOfReject), mesmo quando quem chamou
 * já tratou tudo isso como uma devolução para ajuste, não uma rejeição de verdade. O MPO pediu
 * explicitamente que essas duas coisas fiquem distintas (ver HU010 CA04). Esta classe replica só
 * a parte de "devolver item pro workspace do depositante" do método nativo, sem a proveniência
 * duplicada nem o e-mail de rejeição.
 */
public class CustomXmlWorkflowServiceImpl extends XmlWorkflowServiceImpl {

    private static final Logger log = LogManager.getLogger(CustomXmlWorkflowServiceImpl.class);

    /**
     * Devolve o item pro workspace do depositante por causa de um pedido de ajuste, sem
     * caracterizar rejeição: sem o "Rejected by" na proveniência e sem o e-mail nativo de
     * rejeição. Quem chama já deve ter gravado a proveniência e enviado a notificação própria
     * antes de chamar este método.
     *
     * @param context o contexto do DSpace
     * @param wi      o item de workflow a devolver
     * @param e       o curador que está devolvendo o item
     * @return o WorkspaceItem resultante, já no workspace do depositante
     */
    public WorkspaceItem sendWorkflowItemBackForAdjustment(Context context, XmlWorkflowItem wi, EPerson e)
            throws SQLException, AuthorizeException, IOException {

        String workflowID = null;
        String currentStepId = null;
        String currentActionConfigId = null;
        ClaimedTask claimedTask = claimedTaskService.findByWorkflowIdAndEPerson(context, wi, e);
        if (claimedTask != null) {
            workflowID = claimedTask.getWorkflowID();
            currentStepId = claimedTask.getStepID();
            currentActionConfigId = claimedTask.getActionID();
        }
        context.turnOffAuthorisationSystem();

        Item myitem = wi.getItem();

        // Limpa metadado interno de controle de workflow, igual o nativo faz na rejeição
        itemService.clearMetadata(context, myitem, WorkflowRequirementsService.WORKFLOW_SCHEMA,
                Item.ANY, Item.ANY, Item.ANY);
        itemService.update(context, myitem);

        // Remove as políticas de acesso do curador/revisor sobre o item, igual o nativo
        removeUserItemPolicies(context, myitem, e);
        revokeReviewerPolicies(context, myitem);

        // Converte de volta em item de workspace do depositante — sem notifyOfReject
        WorkspaceItem wsi = returnToWorkspace(context, wi);

        log.info(LogHelper.getHeader(context, "return_for_adjustment", "workflow_item_id="
                + wi.getID() + "item_id=" + wi.getItem().getID()
                + "collection_id=" + wi.getCollection().getID() + "eperson_id="
                + e.getID()));

        logWorkflowEvent(context, workflowID, currentStepId, currentActionConfigId, wi, e, null, null);

        context.restoreAuthSystemState();
        return wsi;
    }
}
