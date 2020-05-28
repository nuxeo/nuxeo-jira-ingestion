package org.nuxeo.ecm.jira.ingestion.automation;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.runtime.api.Framework;

@Operation(id = JiraIngestionOperation.ID, category = Constants.CAT_DOCUMENT, label = "Ingest new Nuxeo jira tickets to instance")
public class JiraIngestionOperation {

    public static final String ID = "Jira.UpdateFromJira";
    private static final Logger log = LogManager.getLogger(JiraIgestionWork.class);
    
    @Context
    public CoreSession coreSession;

    @Param(name = "document", description = "A document", required = false)
    protected DocumentModel documentModel;

    @OperationMethod
    public DocumentModel run(final DocumentModel doc) {
        WorkManager workManager = Framework.getService(WorkManager.class);
        JiraIgestionWork work = new JiraIgestionWork(doc.getRepositoryName(), doc.getId());
        log.error(String.format("Scheduling work: storyboard of Video document %s.", doc));
        workManager.schedule(work, true);
        return null;
    }

    

    @OperationMethod
    public DocumentModel run(final DocumentRef docRef) {
        final DocumentModel docModel = coreSession.getDocument(docRef);
        return run(docModel);
    }

    @OperationMethod
    public DocumentModel run() {
        return run(this.documentModel);
    }

}