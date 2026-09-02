/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.audit;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.dspace.core.AbstractHibernateDAO;
import org.dspace.core.Context;
import org.dspace.eperson.AuditLog;
import org.hibernate.Session;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {

    private static class HibernateSessionHelper extends AbstractHibernateDAO<Object> {
        public Session getSession(Context context) throws SQLException {
            return super.getHibernateSession(context);
        }
    }

    private final HibernateSessionHelper sessionHelper = new HibernateSessionHelper();

    public List<AuditLog> findAll(Context context) throws SQLException {
        Session session = sessionHelper.getSession(context);
        
        String sql = "SELECT id, eperson_id, action_type, subject_type, subject_id, event_timestamp, details, subject_name " +
                     "FROM custom_audit_log ORDER BY event_timestamp DESC";
                     
        @SuppressWarnings("unchecked")
        List<Object[]> rows = session.createNativeQuery(sql).getResultList();
        List<AuditLog> logs = new ArrayList<>();
        
        for (Object[] row : rows) {
            AuditLog log = new AuditLog();
            log.setId((UUID) row[0]);
            log.setEpersonId(row[1] != null ? (UUID) row[1] : null);
            log.setActionType((String) row[2]);
            log.setSubjectType((String) row[3]);
            log.setSubjectId((UUID) row[4]);
            
            Object tsObj = row[5];
            if (tsObj instanceof java.sql.Timestamp) {
                log.setTimestamp(new Date(((java.sql.Timestamp) tsObj).getTime()));
            } else if (tsObj instanceof Date) {
                log.setTimestamp((Date) tsObj);
            }
            
            log.setDetails((String) row[6]);
            log.setSubjectName((String) row[7]);
            
            logs.add(log);
        }
        return logs;
    }
}