/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.custom.audit;

import java.sql.SQLException;
import java.util.Date;
import java.util.UUID;

import org.dspace.core.AbstractHibernateDAO;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.EPersonService;
import org.dspace.eperson.service.GroupService;
import org.dspace.event.Consumer;
import org.dspace.event.Event;
import org.hibernate.Session;

public class SecurityAuditConsumer implements Consumer {

    private static class HibernateSessionHelper extends AbstractHibernateDAO<Object> {
        public Session getSession(Context context) throws SQLException {
            return super.getHibernateSession(context);
        }
    }

    private HibernateSessionHelper sessionHelper;
    private EPersonService ePersonService;
    private GroupService groupService;

    @Override
    public void initialize() throws Exception {
        sessionHelper = new HibernateSessionHelper();
        ePersonService = EPersonServiceFactory.getInstance().getEPersonService();
        groupService = EPersonServiceFactory.getInstance().getGroupService();
    }

    @Override
    public void consume(Context ctx, Event event) throws Exception {
        int subjectType = event.getSubjectType();
        int eventType = event.getEventType();

        if (subjectType == Constants.EPERSON || subjectType == Constants.GROUP) {
            
            EPerson currentUser = ctx.getCurrentUser();
            UUID userId = (currentUser != null) ? currentUser.getID() : null;
            
            Object detailObj = event.getDetail();
            String detalhes = (detailObj != null) ? detailObj.toString() : "Ação realizada via sistema";
            String subjectName = "Registro Excluído ou ID " + event.getSubjectID();
            
            try {
                if (eventType != Event.DELETE) {
                    if (subjectType == Constants.EPERSON) {
                        EPerson eperson = ePersonService.find(ctx, event.getSubjectID());
                        if (eperson != null) subjectName = eperson.getEmail();
                        
                    } else if (subjectType == Constants.GROUP) {
                        Group group = groupService.find(ctx, event.getSubjectID());
                        if (group != null) subjectName = group.getName();
                    }
                }
            } catch (Exception e) {
                // Evita interromper o fluxo do DSpace em caso de erro na busca
            }

            String sql = "INSERT INTO custom_audit_log (id, eperson_id, action_type, subject_type, subject_id, subject_name, event_timestamp, details) " +
                         "VALUES (:id, :epersonId, :actionType, :subjectType, :subjectId, :subjectName, :eventTimestamp, :details)";

            Session session = sessionHelper.getSession(ctx);
            session.createNativeQuery(sql)
                   .setParameter("id", UUID.randomUUID())
                   .setParameter("epersonId", userId)
                   .setParameter("actionType", getEventTypeText(eventType))
                   .setParameter("subjectType", Constants.typeText[subjectType])
                   .setParameter("subjectId", event.getSubjectID())
                   .setParameter("subjectName", subjectName)
                   .setParameter("eventTimestamp", new Date())
                   .setParameter("details", detalhes)
                   .executeUpdate();
        }
    }

    private String getEventTypeText(int eventType) {
        switch (eventType) {
            case Event.CREATE: return "CREATE";
            case Event.MODIFY:
            case Event.MODIFY_METADATA: return "MODIFY";
            case Event.DELETE: return "DELETE";
            case Event.ADD: return "ADD_MEMBER";
            case Event.REMOVE: return "REMOVE_MEMBER";
            default: return "UNKNOWN";
        }
    }

    @Override
    public void end(Context ctx) throws Exception {}
    @Override
    public void finish(Context ctx) throws Exception {}
}