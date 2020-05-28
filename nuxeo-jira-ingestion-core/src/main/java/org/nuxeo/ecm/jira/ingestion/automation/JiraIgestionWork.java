package org.nuxeo.ecm.jira.ingestion.automation;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentNotFoundException;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.IterableQueryResult;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.query.sql.NXQL;
import org.nuxeo.ecm.core.work.AbstractWork;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class JiraIgestionWork extends AbstractWork {

    /**
     *
     */
    private static final long serialVersionUID = 124L;

    private static final Logger log = LogManager.getLogger(JiraIgestionWork.class);

    public static final String CATEGORY = "jiraIngestion";

    private static final Map<String, String[]> propertyMap;
    static {
        final Map<String, String[]> aMap = new HashMap<>();
        aMap.put("title", new String[] { "dc:title", null, null });
        aMap.put("link", new String[] { "dc:source", null, null });
        aMap.put("project", new String[] { "tc:project", null, null });
        aMap.put("description", new String[] { "tc:description", null, null });
        aMap.put("environment", new String[] { "tc:environment", null, null });
        aMap.put("key", new String[] { "name", null, null });
        aMap.put("summary", new String[] { "tc:summary", null, null });
        aMap.put("type", new String[] { "tc:issueCategory", null, null });
        aMap.put("priority", new String[] { "tc:priority", null, null });
        aMap.put("status", new String[] { "tc:status", null, null });
        aMap.put("resolution", new String[] { "tc:resolution", null, null });
        aMap.put("assignee", new String[] { "tc:assignee", null, null });
        aMap.put("reporter", new String[] { "tc:reporter", null, null });
        aMap.put("created", new String[] { "tc:created", null, "true" });
        aMap.put("updated", new String[] { "tc:updated", null, "true" });
        aMap.put("resolved", new String[] { "tc:resolved", null, "true" });
        aMap.put("fixVersion", new String[] { "tc:fixVersions", "true", null });
        aMap.put("component", new String[] { "tc:components", "true", null });
        propertyMap = Collections.unmodifiableMap(aMap);
    }

    protected static String computeIdPrefix(final String repositoryName, final String docId) {
        return repositoryName + ':' + docId + ":videostoryboard:";
    }

    public JiraIgestionWork(final String repositoryName, final String docId) {
        super(repositoryName + ':' + docId + ":" + CATEGORY +":");
        setDocument(repositoryName, docId);
    }

    @Override
    public boolean isIdempotent() {
        return false;
    }

    @Override
    public String getCategory() {
        return CATEGORY;
    }

    @Override
    public String getTitle() {
        return "Jira Igestion: " + getId();
    }

    @Override
    public void work() {
        setProgress(Progress.PROGRESS_INDETERMINATE);
        openSystemSession();


        // update storyboard
        setStatus("Starting ingestion");
        Date currentStart = new Date(0);
        log.debug("Jira started ");
        String lastUpdated = "SELECT tc:updated FROM Ticket where " + NXQL.ECM_PARENTID + " = '" + docId + "' AND " + NXQL.ECM_ISTRASHED + " = 0 and " + NXQL.ECM_ISPROXY + " = 0 order by tc:updated desc";
        try (IterableQueryResult res = session.queryAndFetch(lastUpdated, NXQL.NXQL)) {
            if(res.size() > 0){
                Map<String,Serializable> item = res.iterator().next();
                currentStart = ((Calendar)item.get("tc:updated")).getTime();
            } 
        }
        DocumentModel jiraDomain = session.getDocument(new IdRef(docId));
        String jDUrl,jDPage;
        final String JiraUrl = ("JiraDomain".compareTo(jiraDomain.getType()) == 0 && jiraDomain.getPropertyValue("jd:url") != null &&  !(jDUrl = (String) jiraDomain.getPropertyValue("jd:url")).isEmpty())?jDUrl:"https://jira.nuxeo.com";
        final String JiraPage = ("JiraDomain".compareTo(jiraDomain.getType()) == 0 && jiraDomain.getPropertyValue("jd:page") != null &&  !(jDPage = (String) jiraDomain.getPropertyValue("jd:page")).isEmpty())?jDPage:"100";
        final Map<String, Serializable> properties = new HashMap<>();
        List<String> existingKeys = new ArrayList<String>();
        log.debug("Jira domain: " + JiraUrl);
        log.debug("Jira Page Size: " + JiraPage);
        
        while (true) {
            
            

            // get video blob
            final DocumentModel doc = session.getDocument(new IdRef(docId));
            final String pattern = "yyyy/MM/dd HH:mm";
            final SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
            String date = simpleDateFormat.format(currentStart);
            String dateEnc = "";
            String removeKeyClause = "";
            log.debug("Jira date: " + date);
            try {
                dateEnc = URLEncoder.encode(date, StandardCharsets.UTF_8.toString());
                if(existingKeys.size() > 0){
                    removeKeyClause = URLEncoder.encode("and issuekey not in  (" + String.join(",", existingKeys), StandardCharsets.UTF_8.toString()) + ")";
                }
            } catch (final UnsupportedEncodingException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                return;
            }
            
            final String requestURL = JiraUrl + "/sr/jira.issueviews:searchrequest-xml/temp/SearchRequest.xml?jqlQuery=updated+%3E+%22"
            + dateEnc + "%22+" + removeKeyClause + "+ORDER+BY+updated+ASC&tempMax=" + JiraPage;

            log.debug("Jira request: " + requestURL);

            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder;
            NodeList items;
            try {
                builder = factory.newDocumentBuilder();
                final Document document = builder.parse(new URL(requestURL).openStream());
                document.getDocumentElement();
                items = document.getElementsByTagName("item");
                if(items.getLength() == 0){
                    setStatus("Done");
                    log.debug("Jira done!");

                    try{
                        commitOrRollbackTransaction();
                    } finally {
                        cleanUp(true, null);
                    }
                    return;
                }
                for (int i = 0; i < items.getLength(); i++) {
                    
                    final Node item = items.item(i);
                    if (item.getNodeType() == Node.ELEMENT_NODE) {
                        final Element elem = (Element) item;
                        propertyMap.forEach((k, v) -> {
                            final NodeList nodes = elem.getElementsByTagName(k);
                            Serializable value = null;
                            if (v[1] != null) {
                                final List<Serializable> values = new ArrayList<Serializable>();
                                for (int j = 0; j < nodes.getLength(); j++) {
                                    values.add(nodes.item(j).getTextContent());
                                }
                                value = (java.io.Serializable) values;
                            } else if(nodes.getLength() > 0){

                                value = nodes.item(0).getTextContent();
                                if (v[2] != null && value != null && !((String) value).isEmpty()) {
                                    final SimpleDateFormat df = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z");
                                    try {
                                        value = df.parse((String) value);
                                    } catch (final ParseException e) {
                                        // TODO Auto-generated catch block
                                        e.printStackTrace();
                                    }
                                }
                            }
                            if (value != null) {
                                properties.put(v[0], value);
                            }

                        });

                        // tags
                        final NodeList nodes = elem.getElementsByTagName("customfield");
                        for (int j = 0; j < nodes.getLength(); j++) {
                            final Node customField = nodes.item(j);
                            if (customField.getNodeType() == Node.ELEMENT_NODE) {
                                final Element cfElem = (Element) customField;
                                final NodeList cfnode = cfElem.getElementsByTagName("customfieldname");
                                if (cfnode.getLength() == 1 && "Tags".equals(cfnode.item(0).getTextContent())) {
                                    final NodeList tags = cfElem.getElementsByTagName("label");
                                    final List<Serializable> tagValues = new ArrayList<Serializable>();
                                    for (int tagi = 0; tagi < tags.getLength(); ++tagi) {
                                        tagValues.add(tags.item(tagi).getTextContent());
                                    }
                                    properties.put("tc:tags", (Serializable) tagValues);
                                }
                            }
                        }
                        if(properties.containsKey("tc:updated") && properties.get("tc:updated") != null){
                            setStatus("On update: " + properties.get("tc:updated").toString());
                            currentStart = (Date) properties.get("tc:updated");
                            if(date.compareTo(simpleDateFormat.format(currentStart)) == 0 ) {
                                existingKeys.add('"' + ((String) properties.get("name")) + '"');
                            } else {
                                existingKeys.clear();
                            }
                            log.debug("Jira updated latest date: " + currentStart.toString());

                        }
                        this.CreateOrUpdateDoc(doc, properties);
                    }

                }

                commitOrRollbackTransaction();
                startTransaction();
            

            } catch (final ParserConfigurationException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (final MalformedURLException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (final SAXException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (final IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } 
            
        }


        

    }

    protected boolean CreateOrUpdateDoc(final DocumentModel doc, final Map<String, Serializable> properties) {
        final boolean created = false;
        final String key = (String) properties.get("name");
        final PathRef path = new PathRef(doc.getPath().toString() + File.separator + key);
        final CoreSession session = doc.getCoreSession();
        
        DocumentModel ticket;
        boolean exist = false;
        try {
            ticket = session.getDocument(path);
            exist = true;
        } catch (final DocumentNotFoundException e) {
            ticket = session.createDocumentModel(doc.getPath().toString(), key, "Ticket");
        }
        properties.put("tc:issueKey", key);
        properties.remove("name");
        for (final Entry<String, Serializable> entry : properties.entrySet()) {
            ticket.getProperty(entry.getKey()).setValue(entry.getValue());
        }

        if (exist) {
            session.saveDocument(ticket);
            log.debug("Jira update: " + key);
        } else {
            session.createDocument(ticket);
            log.debug("Jira create: " + key);
        }
        session.save();

        return created;
    }

}