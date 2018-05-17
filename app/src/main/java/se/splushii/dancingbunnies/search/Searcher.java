package se.splushii.dancingbunnies.search;

import android.util.Log;

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

import se.splushii.dancingbunnies.util.Util;

// TODO: Properly handle exceptions
public class Searcher {
    private static final String LC = Util.getLogContext(Searcher.class);
    private final File indexDirectoryPath;

    private QueryParser queryParser;
    private IndexSearcher indexSearcher;

    public Searcher(File indexDirectoryPath) {
        this.indexDirectoryPath = indexDirectoryPath;
    }

    public boolean initialize() {
        if (indexSearcher != null && queryParser != null) {
            return true;
        }
        IndexReader indexReader;
        try {
            indexReader = DirectoryReader.open(FSDirectory.open(indexDirectoryPath));
        } catch (IOException e) {
            Log.e(LC, e.getMessage());
            return false;
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
        return true;
    }

    public TopDocs search(String queryString) {
        if (queryParser == null || indexSearcher == null) {
            return null;
        }
        Query query;
        try {
            query = queryParser.parse(queryString);
        } catch (ParseException e) {
            Log.e(LC, e.getMessage());
            return null;
        }
        TopDocs topDocs;
        try {
            // TODO: Add possibility to drag for more results than 100
            topDocs = indexSearcher.search(query, 100);
        } catch (IOException e) {
            Log.e(LC, e.getMessage());
            return null;
        }
        return topDocs;
    }

    public Document getDocument(ScoreDoc sd) {
        if (indexSearcher == null) {
            return null;
        }
        try {
            return indexSearcher.doc(sd.doc);
        } catch (IOException e) {
            Log.e(LC, e.getMessage());
        }
        return null;
    }
}
