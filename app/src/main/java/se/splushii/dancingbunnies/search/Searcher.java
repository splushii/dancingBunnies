package se.splushii.dancingbunnies.search;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import java.io.File;
import java.io.IOException;

// TODO: Properly handle exceptions
public class Searcher {
    private QueryParser queryParser;
    private IndexSearcher indexSearcher;

    public Searcher(File indexDirectoryPath) {
        IndexReader indexReader;
        try {
            indexReader = DirectoryReader.open(FSDirectory.open(indexDirectoryPath));
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        indexSearcher = new IndexSearcher(indexReader);
        Analyzer analyzer = new StandardAnalyzer(Indexer.LUCENE_VERSION);
        String[] defaultFields = {
                Indexer.FIELD_TITLE,
                Indexer.FIELD_ALBUM,
                Indexer.FIELD_ARTIST
        };
        queryParser = new MultiFieldQueryParser(
                Version.LUCENE_48,
                defaultFields,
                analyzer
        );
    }

    public TopDocs search(String queryString) {
        Query query;
        try {
            query = queryParser.parse(queryString);
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
        TopDocs topDocs;
        try {
            // TODO: Add possibility to drag for more results than 100
            topDocs = indexSearcher.search(query, 100);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return topDocs;
    }

    public Document getDocument(ScoreDoc sd) {
        try {
            return indexSearcher.doc(sd.doc);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
