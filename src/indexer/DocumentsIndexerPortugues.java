/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package indexer;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.pt.PortugueseAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;


/**
 *
 * @author Debasis
 */

public class DocumentsIndexerPortugues {
    private Properties prop;
    private File indexDir;
    private IndexWriter writer;
    private Analyzer analyzer;
    private int docIdx = 0;
    private int docContentIdx;
    private int docDomainIdx;
    private String separator;

    private List<String> buildStopwordList(String stopwordFileName) {
        List<String> stopwords = new ArrayList<>();
        String stopFile = prop.getProperty(stopwordFileName);        
        String line;

        try (FileReader fr = new FileReader(stopFile);
            BufferedReader br = new BufferedReader(fr)) {
            while ( (line = br.readLine()) != null ) {
                stopwords.add(line.trim());
            }
            br.close();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        return stopwords;
    }

    Analyzer constructAnalyzer() {
        return new PortugueseAnalyzer(
            StopFilter.makeStopSet(buildStopwordList("stopfile")));
    }
    
    private DocumentsIndexerPortugues(String propFile) throws Exception {
        prop = new Properties();
        prop.load(new FileReader(propFile));                
        analyzer = constructAnalyzer();            
        String indexPath = prop.getProperty("index");        
        indexDir = new File(indexPath);
        separator = prop.getProperty("separator");
        docContentIdx = Integer.parseInt(prop.getProperty("doc_content_pos"));
        docDomainIdx = Integer.parseInt(prop.getProperty("doc_domain_pos"));
    }
    private DocumentsIndexerPortugues(String _separator, int _docContentIdx, int _docDomainIdx, String indexPath, String _coll) throws Exception {
        prop = new Properties();
        prop.setProperty("coll", _coll);
        prop.setProperty("stopfile", "./stopwords_pt.txt");
        analyzer = constructAnalyzer();
        indexDir = new File(indexPath);
        separator = _separator;
        docContentIdx = _docContentIdx;
        docDomainIdx = _docDomainIdx;

    }
    
    public Analyzer getAnalyzer() { return analyzer; }

    void processAll() throws Exception {
        System.out.println("Indexing " + prop.getProperty("coll") + " collection...");
        IndexWriterConfig iwcfg = new IndexWriterConfig(analyzer);
        iwcfg.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        writer = new IndexWriter(FSDirectory.open(indexDir.toPath()), iwcfg);
        indexAll();
        System.out.println(docIdx + " docs added");
        writer.close();
    }

    void indexAll() throws Exception {
        if (writer == null) {
            System.err.println("Skipping indexing... Index already exists at " + indexDir.getName() + "!!");
            return;
        }
        
        File topDir = new File(prop.getProperty("coll"));
        indexDirectory(topDir);
    }

    private void indexDirectory(File dir) throws Exception {
        File[] files = dir.listFiles();
        for (int i=0; i < files.length; i++) {
            File f = files[i];
            if (f.isDirectory()) {
                System.out.println("Indexing directory " + f.getName());
                indexDirectory(f);  // recurse
            }
            else
                indexFile(f);
        }
    }
    
    void indexFile(File file) throws Exception {
        FileReader fr = new FileReader(file);
        BufferedReader br = new BufferedReader(fr);
        String line;
        Document doc;

        System.out.println("Indexing file: " + file.getName());
        while ((line = br.readLine()) != null) {
            String[] row = line.split(separator);
            doc = constructDoc(row);
            if (doc != null) {
                writer.addDocument(doc);
            }
        }
    }
    
    Document constructDoc(String[] row) throws Exception {
        if (row.length < 2) return  null;
        Document doc = new Document();
        String docDomainName = row[docDomainIdx];
        String docTextElt = row[docContentIdx];
        doc.add(new Field(WMTIndexer.FIELD_DOMAIN_ID, docDomainName, Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(WMTIndexer.FIELD_DOC_NO, String.valueOf(docIdx), Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(WMTIndexer.FIELD_URL, String.valueOf(docIdx++), Field.Store.YES, Field.Index.NOT_ANALYZED));
        String content = docTextElt;
        if (content.equals("null"))
            return null;
        doc.add(new Field(WMTIndexer.FIELD_ANALYZED_CONTENT, docTextElt,
                Field.Store.YES, Field.Index.ANALYZED, Field.TermVector.YES));
        return doc;
    }
    
    
    public static void main(String[] args) {
//        if (args.length == 0) {
//            args = new String[1];
//            System.out.println("Usage: java DocumentsIndexer <prop-file>");
//            args[0] = "indexes_properties/docs_index.properties";
//        }
//
//        try {
//            DocumentsIndexer indexer = new DocumentsIndexer(args[0]);
//            indexer.processAll();
//        }
//        catch (Exception ex) {
//            ex.printStackTrace();
//        }
//        System.out.println("Usage: java DocumentsIndexer <prop-file>");

        String dataset_path[] = {"./dataset/10newsgroups"};
        String indexes_path[] = {"./index/10newsgroups"};
        String separator = ",";
        int domain_pos = 0, content_pos = 1;
        for (int i = 0; i < dataset_path.length; i++) {
            try {
                DocumentsIndexerPortugues indexer = new DocumentsIndexerPortugues(separator, content_pos, domain_pos, indexes_path[i], dataset_path[i]);
                indexer.processAll();
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
//            break;
        }

    }
}
