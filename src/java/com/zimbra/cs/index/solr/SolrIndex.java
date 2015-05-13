package com.zimbra.cs.index.solr;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.http.NoHttpResponseException;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient.RemoteSolrException;
import org.apache.solr.client.solrj.request.AbstractUpdateRequest.ACTION;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.client.solrj.response.CoreAdminResponse;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.CollectionParams.CollectionAction;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.util.NamedList;

import com.zimbra.common.account.ZAttrProvisioning;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.ZimbraHttpClientManager;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.index.IndexDocument;
import com.zimbra.cs.index.IndexStore;
import com.zimbra.cs.index.Indexer;
import com.zimbra.cs.index.LuceneFields;
import com.zimbra.cs.index.ZimbraIndexSearcher;
import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.Mailbox.IndexItemEntry;
import com.zimbra.cs.util.ProvisioningUtil;
import com.zimbra.cs.util.Zimbra;

/**
 * Index adapter for standalone Solr
 * @author gsolovyev
 *
 */
public class SolrIndex extends SolrIndexBase {
    private final CloseableHttpClient httpClient;
    private boolean solrCoreProvisioned = false;
    
    protected String getBaseURL() throws ServiceException {
       return Provisioning.getInstance().getLocalServer().getIndexURL().substring(5); 
    }
    
    protected SolrIndex(String accountId, CloseableHttpClient httpClient) {
        this.httpClient = httpClient; 
        this.accountId = accountId;
    }
    
    @Override
    /**
     * Gets the latest commit version and generation from Solr
     */
    public long getLatestIndexGeneration(String accountId) throws ServiceException {
        long version = 0L;
        ModifiableSolrParams params = new ModifiableSolrParams();
        params.set(COMMAND, CMD_INDEX_VERSION);
        params.set(CommonParams.WT, "javabin");
        params.set(CommonParams.QT, "/replication");
        QueryRequest req = new QueryRequest(params);
        @SuppressWarnings("rawtypes")
        NamedList rsp;
        SolrClient solrServer = getSolrServer();
        setupRequest(req, solrServer);
        try {
            rsp = solrServer.request(req);
            version = (Long) rsp.get(GENERATION);
        } catch (SolrServerException | IOException e) {
          throw ServiceException.FAILURE(e.getMessage(),e);
        } finally {
            shutdown(solrServer);
        }
        return version;
    }
    
    /**
     * Fetches the list of index files from Solr using Solr Replication RequestHandler 
     * See {@link https://cwiki.apache.org/confluence/display/solr/Index+Replication}
     * @param gen generation of index. Required by Replication RequestHandler
     * @throws BackupServiceException
     */
    @Override
    public List<Map<String, Object>> fetchFileList(long gen, String accountId) throws ServiceException {
        ModifiableSolrParams params = new ModifiableSolrParams();
        params.set(COMMAND, CMD_GET_FILE_LIST);
        params.set(GENERATION, String.valueOf(gen));
        params.set(CommonParams.WT, "javabin");
        params.set(CommonParams.QT, "/replication");
        QueryRequest req = new QueryRequest(params);
        SolrClient solrServer = getSolrServer();
        setupRequest(req, solrServer);
        try {
            @SuppressWarnings("rawtypes")
            NamedList response = solrServer.request(req);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> files = (List<Map<String, Object>>) response
                    .get(CMD_GET_FILE_LIST);
            if (files != null) {
                return files;
            } else {
                ZimbraLog.index.error("No files to download for index generation: "
                        + gen + " account: " + accountId);
                return Collections.emptyList();
            }
        } catch (SolrServerException | IOException e) {
            throw ServiceException.FAILURE(e.getMessage(), e);
        } finally {
            shutdown(solrServer);
        }
    }

    @Override
    public boolean indexExists() {
        if(!solrCoreProvisioned) {
            int maxTries;
            try {
                maxTries = Provisioning.getInstance().getLocalServer().getSolrMaxRetries()+1;
            } catch (ServiceException e1) {
                maxTries = 1;
            }
            while(maxTries-- > 0 && !solrCoreProvisioned) {
                HttpSolrClient solrServer = null;
                try {
                    solrServer = (HttpSolrClient)getSolrServer();
                    ((HttpSolrClient)solrServer).setBaseURL(getBaseURL());
                    CoreAdminResponse resp = CoreAdminRequest.getStatus(accountId, solrServer);
                    solrCoreProvisioned = resp.getCoreStatus(accountId).size() > 0;
                } catch (SolrServerException | SolrException e) {
                    if(e.getCause() instanceof NoHttpResponseException) {
                        this.httpClient.getConnectionManager().closeExpiredConnections();
                    }
                    ZimbraLog.index.info("Solr Core for account %s does not exist", accountId);
                }  catch (IOException e) {
                     ZimbraLog.index.error("failed to check if Solr Core for account %s exists", accountId,e);
                }  catch (ServiceException e) {
                    ZimbraLog.index.error("failed to check if Solr Core for account %s exists", accountId,e);
                } finally {
                    shutdown(solrServer);
                }
            }
        }
        return solrCoreProvisioned;
    }

    @Override
    public void initIndex() throws IOException, ServiceException {
        solrCoreProvisioned = false;
        SolrClient solrServer = getSolrServer();
        try {
            ((HttpSolrClient)solrServer).setBaseURL(getBaseURL());
            ModifiableSolrParams params = new ModifiableSolrParams();
            params.set("action", CollectionAction.CREATE.toString());
            params.set("name", accountId);
            params.set("configSet","zimbra");
            SolrRequest req = new QueryRequest(params);
            req.setPath("/admin/cores");
            req.process(solrServer);
            //TODO check for errors
            ZimbraLog.index.info("Created Solr Core for account ", accountId);
        } catch (SolrServerException e) {
            String errorMsg = String.format("Problem creating new Solr Core for account %s",accountId);
            ZimbraLog.index.error(errorMsg, e);
            throw ServiceException.FAILURE(errorMsg,e);
        } catch (RemoteSolrException e) {
            if(e.getMessage() != null && e.getMessage().indexOf("already exists") > 0) {
                solrCoreProvisioned = true;
                return;
            }
            String errorMsg = String.format("Problem creating new Solr Core for account %s",accountId);
            ZimbraLog.index.error(errorMsg, e);
            throw ServiceException.FAILURE(errorMsg,e);
        } catch (SolrException e) {
            String errorMsg = String.format("Problem creating new Solr Core for account %s",accountId);
            ZimbraLog.index.error(errorMsg, e);
            throw ServiceException.FAILURE(errorMsg,e);
        } catch (IOException e) {
            String errorMsg = String.format("Problem creating new Solr Core for account %s",accountId);
            ZimbraLog.index.error(errorMsg, e);
            throw ServiceException.FAILURE(errorMsg,e);
        }  finally {
            shutdown(solrServer);
        }
        solrCoreProvisioned = true;
    }

    @Override
    public Indexer openIndexer() throws IOException, ServiceException {
        if(!indexExists()) {
            initIndex();
        }
        return new SolrIndexer();
    }

    @Override
    public ZimbraIndexSearcher openSearcher() throws IOException, ServiceException {
        /*if(!indexExists()) {
            initIndex();
        }*/
        final SolrIndexReader reader = new SolrIndexReader();
        return new SolrIndexSearcher(reader);
    }

    @Override
    public void evict() {
        // TODO Auto-generated method stub

    }

    @Override
    public void deleteIndex() throws IOException, ServiceException {
        if(indexExists()) {
            SolrClient solrServer = getSolrServer();
            try {
                ((HttpSolrClient)solrServer).setBaseURL(getBaseURL());
                CoreAdminRequest.unloadCore(accountId, true, true, solrServer);
                solrCoreProvisioned = false;
                //TODO check for errors
            } catch (SolrServerException e) {
                ZimbraLog.index.error("Problem deleting Solr Core" , e);
                throw ServiceException.FAILURE("Problem deleting Solr Core",e);
            } catch (IOException e) {
                ZimbraLog.index.error("Problem deleting Solr Core" , e);
                throw e;
            } finally {
                shutdown(solrServer);
            }
        }
    }

    @Override
    public void setupRequest(Object obj, SolrClient solrServer) throws ServiceException {
        ((HttpSolrClient)solrServer).setBaseURL(getBaseURL() + "/" + accountId);
        if (obj instanceof UpdateRequest) {
            if(ProvisioningUtil.getServerAttribute(ZAttrProvisioning.A_zimbraIndexManualCommit, false)) {
                ((UpdateRequest) obj).setAction(ACTION.COMMIT, true, true, false);
            }
        }
    }

    @Override
    public SolrClient getSolrServer() throws ServiceException {
        HttpSolrClient server = new HttpSolrClient(getBaseURL() + "/" + accountId, httpClient);
        return server;
    }

    @Override
    public void shutdown(SolrClient server) {
        if(server != null) {
            server.shutdown();
        }
    }

    public static final class Factory implements IndexStore.Factory {
        public Factory() {
            ZimbraLog.index.info("Created SolrlIndexStore.Factory\n");
        }

        @Override
        public SolrIndex getIndexStore(String accountId) {
            return new SolrIndex(accountId, Zimbra.getAppContext().getBean(ZimbraHttpClientManager.class).getInternalHttpClient());
        }

        /**
         * Cleanup any caches etc associated with the IndexStore
         */
        @Override
        public void destroy() {
            ZimbraLog.index.info("Destroyed SolrlIndexStore.Factory\n");
        }
    }

    private class SolrIndexer extends SolrIndexBase.SolrIndexer {
        @Override
        public void add(List<Mailbox.IndexItemEntry> entries) throws IOException, ServiceException {
            if(!indexExists()) {
                initIndex();
            }
            SolrClient solrServer = getSolrServer();
            UpdateRequest req = new UpdateRequest();
            setupRequest(req, solrServer);
            for (IndexItemEntry entry : entries) {
                if (entry.documents == null) {
                    ZimbraLog.index.warn("NULL index data item=%s", entry);
                    continue;
                }
                int partNum = 1;
                for (IndexDocument doc : entry.documents) {
                    SolrInputDocument solrDoc;
                    // doc can be shared by multiple threads if multiple mailboxes are referenced in a single email
                    synchronized (doc) {
                        setFields(entry.item, doc);
                        solrDoc = doc.toInputDocument();
                        solrDoc.addField(SOLR_ID_FIELD, String.format("%d_%d",entry.item.getId(),partNum));
                        partNum++;
                        if (ZimbraLog.index.isTraceEnabled()) {
                            ZimbraLog.index.trace("Adding solr document %s", solrDoc.toString());
                        }
                    }
                    req.add(solrDoc);
                }
            }
            try {
                if(ProvisioningUtil.getServerAttribute(ZAttrProvisioning.A_zimbraIndexManualCommit, false)) {
                    incrementUpdateCounter(solrServer);
                }
                processRequest(solrServer, req);
            } catch (SolrServerException e) {
                ZimbraLog.index.error("Problem indexing documents", e);
            }  finally {
                shutdown(solrServer);
            }
        }

        @Override
        public void addDocument(MailItem item, List<IndexDocument> docs) throws ServiceException {
            if (docs == null || docs.isEmpty()) {
                return;
            }
            try {
                if(!indexExists()) {
                    initIndex();
                }
            } catch (IOException e) {
                throw ServiceException.FAILURE(String.format(Locale.US, "Failed to index mail item with ID %d for Account %s ", item.getId(), accountId), e);
            }

            int partNum = 1;
            for (IndexDocument doc : docs) {
                SolrInputDocument solrDoc;
                // doc can be shared by multiple threads if multiple mailboxes are referenced in a single email
                synchronized (doc) {
                    setFields(item, doc);
                    solrDoc = doc.toInputDocument();
                    solrDoc.addField(SOLR_ID_FIELD, String.format("%d_%d",item.getId(),partNum));
                    partNum++;
                    if (ZimbraLog.index.isTraceEnabled()) {
                        ZimbraLog.index.trace("Adding solr document %s", solrDoc.toString());
                    }
                }
                SolrClient solrServer = getSolrServer();
                UpdateRequest req = new UpdateRequest();
                setupRequest(req, solrServer);
                req.add(solrDoc);
                try {
                    if(ProvisioningUtil.getServerAttribute(ZAttrProvisioning.A_zimbraIndexManualCommit, false)) {
                        incrementUpdateCounter(solrServer);
                    }
                    processRequest(solrServer, req);
                } catch (SolrServerException | IOException e) {
                    throw ServiceException.FAILURE(String.format(Locale.US, "Failed to index part %d of Mail Item with ID %d for Account %s ", partNum, item.getId(), accountId), e);
                } finally {
                    shutdown(solrServer);
                }
            }
        }
        
        @Override
        public void deleteDocument(List<Integer> ids) throws IOException,ServiceException {
            if(!indexExists()) {
                return;
            }
            SolrClient solrServer = getSolrServer();
            try {
                for (Integer id : ids) {
                    UpdateRequest req = new UpdateRequest().deleteByQuery(String.format("%s:%d",LuceneFields.L_MAILBOX_BLOB_ID,id));
                    setupRequest(req, solrServer);
                    try {
                        if(ProvisioningUtil.getServerAttribute(ZAttrProvisioning.A_zimbraIndexManualCommit, false)) {
                            incrementUpdateCounter(solrServer);
                        }
                        processRequest(solrServer, req);
                        ZimbraLog.index.debug("Deleted document id=%d", id);
                    } catch (SolrServerException e) {
                        ZimbraLog.index.error("Problem deleting document with id=%d", id,e);
                    } 
                }
            } finally {
                shutdown(solrServer);
            }
        }
        
        @Override
        public int maxDocs() {
            SolrClient solrServer = null; 
            try {
                solrServer = getSolrServer();
                ((HttpSolrClient)solrServer).setBaseURL(getBaseURL());
                CoreAdminResponse resp = CoreAdminRequest.getStatus(accountId, solrServer);
                Iterator<Map.Entry<String, NamedList<Object>>> iter = resp.getCoreStatus().iterator();
                while(iter.hasNext()) {
                    Object maxDocs = resp.getCoreStatus(accountId).findRecursive("index","maxDoc");
                    if(maxDocs != null && maxDocs instanceof Integer) {
                        return (int)maxDocs;
                    }
                }
            } catch (IOException e) {
                ZimbraLog.index.error("Caught IOException retrieving maxDocs for mailbox %s", accountId,e );
            } catch (SolrServerException e) {
                ZimbraLog.index.error("Caught SolrServerException retrieving maxDocs for mailbox %s", accountId,e);
            } catch (RemoteSolrException e) {
                ZimbraLog.index.error("Caught RemoteSolrException retrieving maxDocs for mailbox %s", accountId,e);
            } catch (ServiceException e) {
                ZimbraLog.index.error("Caught ServiceException retrieving maxDocs for mailbox %s", accountId,e);
            } finally {
                shutdown(solrServer);
            }
            return 0;
        }
        
    }

    public class SolrIndexReader extends SolrIndexBase.SolrIndexReader {
        @Override
        public int numDeletedDocs() {
            SolrClient solrServer = null;
            try {
                solrServer = getSolrServer();
                ((HttpSolrClient)solrServer).setBaseURL(getBaseURL());
                CoreAdminResponse resp = CoreAdminRequest.getStatus(accountId, solrServer);
                Iterator<Map.Entry<String, NamedList<Object>>> iter = resp.getCoreStatus().iterator();
                while(iter.hasNext()) {
                    Map.Entry<String, NamedList<Object>> entry = iter.next();
                    if(entry.getKey().indexOf(accountId, 0)==0) {
                        return (int)entry.getValue().findRecursive("index","deletedDocs");
                    }
                }
            } catch (IOException e) {
                ZimbraLog.index.error("Caught IOException retrieving number of deleted documents in mailbox %s", accountId,e);
            } catch (SolrServerException e) {
                ZimbraLog.index.error("Caught SolrServerException retrieving number of deleted documents in mailbox %s", accountId,e);
            } catch (RemoteSolrException e) {
                ZimbraLog.index.error("Caught SolrServerException retrieving number of deleted documents in mailbox %s", accountId,e);
            } catch (ServiceException e) {
                ZimbraLog.index.error("Caught ServiceException retrieving number of deleted documents in mailbox %s", accountId,e);
            } finally {
                shutdown(solrServer);
            }
            return 0;
        }
    }
}
