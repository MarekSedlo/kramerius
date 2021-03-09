package cz.kramerius.searchIndex.indexerProcess;


import cz.kramerius.searchIndex.indexer.SolrConfig;
import cz.kramerius.searchIndex.indexer.SolrIndexAccess;
import cz.kramerius.searchIndex.indexer.SolrInput;
import cz.kramerius.searchIndex.indexer.conversions.SolrInputBuilder;
import cz.kramerius.searchIndex.repositoryAccess.KrameriusRepositoryAccessAdapter;
import cz.kramerius.searchIndex.repositoryAccess.nodes.RepositoryNode;
import cz.kramerius.searchIndex.repositoryAccess.nodes.RepositoryNodeManager;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrException;
import org.dom4j.Document;
import org.dom4j.DocumentException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Indexer {
    private static final Logger LOGGER = Logger.getLogger(Indexer.class.getName());

    public static final int INDEXER_VERSION = 1; //this should be updated after every change in logic, that affects full indexation

    private final SolrConfig solrConfig;
    //only state variable
    private boolean shutDown = false;
    //helpers
    private final ReportLogger reportLogger;
    private final KrameriusRepositoryAccessAdapter repositoryConnector;
    private final RepositoryNodeManager nodeManager;

    private final SolrInputBuilder solrInputBuilder;
    private SolrIndexAccess solrIndexer = null;

    public Indexer(KrameriusRepositoryAccessAdapter repositoryConnector, SolrConfig solrConfig, OutputStream reportLoggerStream) {
        this.repositoryConnector = repositoryConnector;
        this.nodeManager = new RepositoryNodeManager(repositoryConnector);
        this.solrInputBuilder = new SolrInputBuilder();
        this.solrConfig = solrConfig;
        this.reportLogger = new ReportLogger(reportLoggerStream);
        init();
    }

    private void report(String message) {
        reportLogger.report(message);
    }

    private void report(String message, Throwable e) {
        reportLogger.report(message, e);
    }

    private void init() {
        report("Parameters");
        report("==============================");
        reportParams();

        report("Initialization");
        report("==============================");

        try {
            solrIndexer = new SolrIndexAccess(solrConfig);
            report("SOLR API connector initialized");
        } catch (Throwable e) {
            report("Initialization error: TemplateException: " + e.getMessage());
            LOGGER.log(Level.SEVERE, "Initialization error", e);
            throw e;
        }
        report(" ");
    }


    public void indexByObjectPid(String pid, IndexationType type, ProgressListener progressListener) {
        if (shutDown) {
            report("Indexer has already been shut down");
        } else {
            long start = System.currentTimeMillis();
            Counters counters = new Counters();
            report("Processing " + pid + " (indexation type: " + type + ")");
            //int limit = 3;
            report("============================================================================================");

            if (type == IndexationType.TREE_AND_FOSTER_TREES) {
                setFullIndexationInProgress(pid);
            }
            RepositoryNode node = nodeManager.getKrameriusNode(pid);
            if (node != null) {
                if (!shutDown) {
                    indexObjectWithCounters(pid, node, counters, true);
                    processChildren(node, true, type, counters);
                }
            }
            if (type == IndexationType.TREE_AND_FOSTER_TREES) {
                clearFullIndexationInProgress(pid);
            }
            commitAfterLastIndexation(counters);

            report(" ");
            if (shutDown) {
                report("Indexer was shut down during execution");
            }
            report("Summary");
            report("=======================================");
            report(" objects found    : " + counters.getFound());
            report(" objects processed: " + counters.getProcessed());
            report(" objects indexed  : " + counters.getIndexed());
            report(" objects removed  : " + counters.getRemoved());
            report(" objects erroneous: " + counters.getErrors());
            report(" *counters include pages from pdf, i.e. not real objects in repository");
            report(" records processing duration: " + formatTime(System.currentTimeMillis() - start));
            report("=======================================");
            if (progressListener != null) {
                progressListener.onFinished(counters.getProcessed(), counters.getFound());
            }
        }
    }

    private void setFullIndexationInProgress(String pid) {
        try {
            SolrInput solrInput = new SolrInput();
            solrInput.addField("pid", pid);
            solrInput.addField("full_indexation_in_progress", Boolean.TRUE.toString());
            solrInput.addField("indexer_version", String.valueOf(INDEXER_VERSION));
            String solrInputStr = solrInput.getDocument().asXML();
            solrIndexer.indexFromXmlString(solrInputStr, false);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void clearFullIndexationInProgress(String pid) {
        report("clearing field full_indexation_in_progress for " + pid);
        //will not work for objects that are not stored and not docValues
        //see https://github.com/ceskaexpedice/kramerius/issues/782
        solrIndexer.setSingleFieldValue(pid, "full_indexation_in_progress", null, false);
    }

    private void processChildren(String parentPid, IndexationType type, Counters counters) {
        RepositoryNode parentNode = nodeManager.getKrameriusNode(parentPid);
        if (parentNode != null) {
            processChildren(parentNode, false, type, counters);
        } else {
            report(" Object node not found: " + parentPid);
        }
    }

    private void indexObjectWithCounters(String pid, Counters counters, boolean isIndexationRoot) {
        if (!shutDown) {
            indexObjectWithCounters(pid, nodeManager.getKrameriusNode(pid), counters, isIndexationRoot);
        }
    }

    private void indexObjectWithCounters(String pid, RepositoryNode repositoryNode, Counters counters, boolean isIndexationRoot) {
        try {
            counters.incrementFound();
            boolean objectAvailable = repositoryNode != null;
            if (!objectAvailable) {
                report("object not found in repository, removing from index as well");
                solrIndexer.deleteById(pid);
                counters.incrementRemoved();
                report("");
            } else {
                LOGGER.info("Indexing " + pid);
                Document foxmlDoc = repositoryConnector.getObjectFoxml(pid, true);
                report("model: " + repositoryNode.getModel());
                report("title: " + repositoryNode.getTitle());
                //the isOcrTextAvailable method (and for other datastreams) is inefficient for implementation through http stack (because of HEAD requests)
                //String ocrText = repositoryConnector.isOcrTextAvailable(pid) ? repositoryConnector.getOcrText(pid) : null;
                String ocrText = normalizeWhitespacesForOcrText(repositoryConnector.getOcrText(pid));
                //System.out.println("ocr: " + ocrText);
                //IMG_FULL mimetype
                String imgFullMime = repositoryConnector.getImgFullMimetype(pid);
                SolrInput solrInput = solrInputBuilder.processObjectFromRepository(foxmlDoc, ocrText, repositoryNode, nodeManager, imgFullMime, isIndexationRoot);
                String solrInputStr = solrInput.getDocument().asXML();
                solrIndexer.indexFromXmlString(solrInputStr, false);
                counters.incrementIndexed();
                report("");
                if ("application/pdf".equals(imgFullMime)) {
                    indexPagesFromPdf(pid, repositoryNode, counters);
                }
            }
        } catch (DocumentException e) {
            counters.incrementErrors();
            report(" Document error", e);
        } catch (IOException e) {
            counters.incrementErrors();
            report(" I/O error", e);
        } catch (SolrServerException e) {
            counters.incrementErrors();
            report(" Solr server error", e);
        }
    }

    private void indexPagesFromPdf(String pid, RepositoryNode repositoryNode, Counters counters) throws IOException, DocumentException, SolrServerException {
        report("object " + pid + " contains PDF, extracting pages");
        InputStream imgFull = repositoryConnector.getImgFull(pid);
        PdfExtractor extractor = new PdfExtractor(pid, imgFull);
        int pages = extractor.getPagesCount();
        for (int i = 0; i < pages; i++) {
            int pageNumber = i + 1;
            counters.incrementFound();
            report("extracting page " + pageNumber + "/" + pages);
            String ocrText = normalizeWhitespacesForOcrText(extractor.getPageText(i));
            SolrInput solrInput = solrInputBuilder.processPageFromPdf(nodeManager, repositoryNode, pageNumber, ocrText);
            String solrInputStr = solrInput.getDocument().asXML();
            solrIndexer.indexFromXmlString(solrInputStr, false);
            counters.incrementIndexed();
            report("");
        }
    }

    private String normalizeWhitespacesForOcrText(String ocrText) {
        return ocrText == null ? null : ocrText
                // ("MAR-\nTIN", "MAR-\r\nTIN", "MAR-\n   TIN", "MAR-\n\tTIN", etc.) -> MARTIN
                .replaceAll("-\\r?\\n\\s*", "")
                // groups of white spaces -> " "
                .replaceAll("\\s+", " ");
    }

    private void processChildren(RepositoryNode parent, boolean isIndexationRoot, IndexationType type, Counters counters) {
        //System.out.println("processChildren (" + parent.getPid() + ")");
        switch (type) {
            case OBJECT: {
                //nothing
            }
            break;
            case OBJECT_AND_CHILDREN: {
                if (isIndexationRoot) {
                    for (String childPid : parent.getPidsOfOwnChildren()) { //index own children
                        indexObjectWithCounters(childPid, counters, false);
                    }
                    for (String childPid : parent.getPidsOfFosterChildren()) { //index foster children
                        indexObjectWithCounters(childPid, counters, false);
                    }
                }
            }
            break;
            case TREE: {
                for (String childPid : parent.getPidsOfOwnChildren()) {
                    indexObjectWithCounters(childPid, counters, false);//index own children
                    processChildren(childPid, type, counters); //process own childrens' trees
                }
            }
            break;
            case TREE_INDEX_ONLY_NEWER: {
                for (String childPid : parent.getPidsOfOwnChildren()) {
                    boolean isNewer = true; //TODO: detect
                    if (isNewer) {
                        indexObjectWithCounters(childPid, counters, false);//index own children
                    }
                    processChildren(childPid, type, counters); //process own childrens' trees
                }
            }
            break;
            case TREE_PROCESS_ONLY_NEWER: {
                for (String childPid : parent.getPidsOfOwnChildren()) {
                    boolean isNewer = true; //TODO: detect
                    if (isNewer) {
                        indexObjectWithCounters(childPid, counters, false);//index own children
                        processChildren(childPid, type, counters); //process own childrens' trees
                    }
                }
            }
            break;
            case TREE_INDEX_ONLY_PAGES: {
                for (String childPid : parent.getPidsOfOwnChildren()) {
                    boolean isPage = false; //TODO: detect
                    if (isPage) {
                        indexObjectWithCounters(childPid, counters, false);//index own children
                    } else {
                        processChildren(childPid, type, counters); //process own childrens' trees
                    }
                }
            }
            break;
            case TREE_INDEX_ONLY_NONPAGES: {
                for (String childPid : parent.getPidsOfOwnChildren()) {
                    boolean isPage = false; //TODO: detect
                    if (!isPage) {
                        indexObjectWithCounters(childPid, counters, false);//index own children
                        processChildren(childPid, type, counters); //process own childrens' trees
                    }
                }
            }
            break;
            case TREE_AND_FOSTER_TREES: {
                for (String childPid : parent.getPidsOfOwnChildren()) {
                    indexObjectWithCounters(childPid, counters, false);//index own children
                    processChildren(childPid, type, counters); //process own childrens' trees
                }
                for (String childPid : parent.getPidsOfFosterChildren()) {
                    indexObjectWithCounters(childPid, counters, false);//index foster children
                    processChildren(childPid, type, counters); //process foster childrens' trees
                }
            }
        }
    }


    private void commitAfterLastIndexation(Counters counters) {
        try {
            solrIndexer.commit();
        } catch (IOException e) {
            counters.incrementErrors();
            report(" I/O error", e);
        } catch (SolrServerException e) {
            counters.incrementErrors();
            report(" Solr server error", e);
        }
    }


    @Deprecated
    public void indexDoc(Document solrDoc, ProgressListener progressListener) {
        List<Document> singleItemList = new ArrayList<>();
        singleItemList.add(solrDoc);
        indexDocBatch(singleItemList, progressListener);
    }

    @Deprecated
    public void indexDocBatch(List<Document> solrDocs, ProgressListener progressListener) {
        long start = System.currentTimeMillis();
        Counters counters = new Counters();
        report("Processing " + counters.getFound() + " records");
        //int limit = 3;
        report("==============================");
        for (Document doc : solrDocs) {
            if (shutDown) {
                report(" stopped ");
                break;
            }
            index(doc, counters, null, false);
        }
        report(" ");
        if (shutDown) {
            report("Indexer was shut down during execution");
        }
        report("Summary");
        report("=======================================");
        report(" records found    : " + counters.getFound());
        report(" records processed: " + counters.getProcessed());
        report(" records indexed  : " + counters.getIndexed());
        report(" records erroneous: " + counters.getErrors());
        report(" records processing duration: " + formatTime(System.currentTimeMillis() - start));
        report("=======================================");
        if (progressListener != null) {
            progressListener.onFinished(counters.getProcessed(), counters.getFound());
        }
    }

    private void index(Document solrDoc, Counters counters, ProgressListener progressListener, boolean explicitCommit) {
        try {
            counters.incrementFound();
            report(" indexing");
            solrIndexer.indexFromXmlString(solrDoc.asXML(), explicitCommit);
            report(" indexed");
            counters.incrementIndexed();
        } catch (IOException e) {
            counters.incrementErrors();
            report(" I/O error", e);
        } catch (SolrServerException e) {
            counters.incrementErrors();
            report(" Solr server error", e);
        } catch (SolrException e) {
            counters.incrementErrors();
            report(" Solr error", e);
        } catch (DocumentException e) {
            counters.incrementErrors();
            report(" Document error", e);
        }
        if (progressListener != null) {
            progressListener.onProgress(counters.getProcessed(), counters.getFound());
        }
    }

    private String formatTime(long millis) {
        long hours = millis / (60 * 60 * 1000);
        long minutes = millis / (60 * 1000) - hours * 60;
        long seconds = (millis / 1000) % 60;
        return String.format("%d:%02d:%02d", hours, minutes, seconds);
    }

    private void reportParams() {
        report(" SOLR API");
        report(" -----------------");
        report("  Base url: " + solrConfig.baseUrl);
        report("  Collection: " + solrConfig.collection);
        report("  Https: " + solrConfig.useHttps);
        //report("  Login: " + solrConfig.login);
        report(" ");
    }

    public void shutDown() {
        shutDown = true;
    }

    public void close() {
        reportLogger.close();
    }

    /*@Deprecated
    public void indexByModel(String model, String type, boolean indexNotIndexed, boolean indexRunningOrError, boolean indexIndexedOutdated, boolean indexIndexed) {
        long start = System.currentTimeMillis();
        int found = 0;
        int processed = 0;
        int nowIgnored = 0;
        int nowIndexed = 0;
        int nowErrors = 0;

        report("TODO: actually run");
        //TODO: 1. iterovat repozitar po nejakych davkach a drzet kurzor
        //TODO: 2. ziskat stavy objektu stylem getIndexationInfoForPids
        //TODO: 3. podle stavovych filtru zpracovat, nebo preskocit
        //TODO: 4. aktualizovat counters
        //TODO: 5. vypsat vysledny stav
        //TODO: 6. reagovat na zastaveni
        //TODO: 7. optimalizace - pokud jsou vsechny filtry index* na true, nebude se kontrolvat solr

        report("Total Summary");
        report("===========================================");
        report(" top-level objects found    : " + found);
        report(" top-level objects processed: " + processed);
        report(" top-level objects indexed  : " + nowIndexed);
        report(" top-level objects ignored  : " + nowIgnored);
        report(" top-level objects erroneous: " + nowErrors);
        report(" total duration: " + formatTime(System.currentTimeMillis() - start));
        report("===========================================");
        *//*if (progressListener != null) {
            progressListener.onFinished(counters.getProcessed(), counters.getFound());
        }*//*
    }*/
}
