/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.eperson;

import java.util.Date;
import java.util.UUID;

public class AuditLog {
    private UUID id;
    private UUID epersonId;
    private String actionType;
    private String subjectType;
    private UUID subjectId;
    private Date timestamp;
    private String details;
    private String subjectName;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    
    public UUID getEpersonId() { return epersonId; }
    public void setEpersonId(UUID epersonId) { this.epersonId = epersonId; }
    
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    
    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
    
    public UUID getSubjectId() { return subjectId; }
    public void setSubjectId(UUID subjectId) { this.subjectId = subjectId; }
    
    public Date getTimestamp() { return timestamp; }
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }
}