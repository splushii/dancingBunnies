package se.splushii.dancingbunnies.search;

import android.content.Context;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import se.splushii.dancingbunnies.util.Util;

// TODO: Properly handle exceptions
public class Searcher {
    public static final String SUB_ID_SEARCH_FIELD_VALUES_SETTINGS_FRAGMENT = "settings_fragment";

    private static final String LC = Util.getLogContext(Searcher.class);

    private File indexDirectoryPath;
    private IndexSearcher indexSearcher;

    private final HashMap<String, String> searchHitsSubQueryStrings = new HashMap<>();
    private final HashMap<String, MutableLiveData<Integer>> searchHitsSubLiveData = new HashMap<>();

    private final HashMap<String, String> searchFieldValuesSubQueryStrings = new HashMap<>();
    private final HashMap<String, String> searchFieldValuesSubFields = new HashMap<>();
    private final HashMap<String, MutableLiveData<Set<String>>> searchFieldValuesSubLiveData = new HashMap<>();

    private static volatile Searcher instance;

    public static synchronized Searcher getInstance() {
        if (instance == null) {
            instance = new Searcher();
        }
        return instance;
    }

    private Searcher() {}

    public boolean initialize(Context context) {
        File indexPath = Indexer.prepareIndexPath(context.getFilesDir());
        if (indexSearcher != null
                && indexDirectoryPath != null
                && indexDirectoryPath.equals(indexPath)) {
            return true;
        }
        indexDirectoryPath = indexPath;
        return setupIndexReader();
    }

    private boolean setupIndexReader() {
        IndexReader indexReader;
        try {
            indexReader = DirectoryReader.open(FSDirectory.open(indexDirectoryPath));
        } catch (IOException e) {
            Log.e(LC, e.getMessage());
            return false;
        }
        indexSearcher = new IndexSearcher(indexReader);
        return true;
    }

    public static QueryParser getQueryParser() {
        Analyzer analyzer = new StandardAnalyzer(Indexer.LUCENE_VERSION);
        String[] defaultFields = {
                Indexer.FIELD_TITLE,
                Indexer.FIELD_ARTIST,
                Indexer.FIELD_ALBUM
        };
        QueryParser queryParser = new MultiFieldQueryParser(
                Version.LUCENE_48,
                defaultFields,
                analyzer
        );
        queryParser.setAllowLeadingWildcard(true);
        return queryParser;
    }

    public TopDocs search(String queryString) {
        return search(queryString, 100);
    }

    public TopDocs search(String queryString, int maxDocs) {
        if (indexSearcher == null) {
            return null;
        }
        Query query;
        try {
            Log.e(LC, "queryString: " + queryString);
            query = getQueryParser().parse(new String(queryString.toCharArray()));
        } catch (ParseException e) {
            Log.e(LC, e.getMessage());
            return null;
        }
        TopDocs topDocs;
        try {
            // TODO: Add possibility to drag for more results than 100
            topDocs = indexSearcher.search(query, maxDocs);
        } catch (IOException e) {
            Log.e(LC, e.getMessage());
            return null;
        }
        return topDocs;
    }

    public LiveData<Integer> searchHitsSubscribe(String subID, String queryString) {
        MutableLiveData<Integer> liveData = new MutableLiveData<>();
        searchHitsSubQueryStrings.put(subID, queryString);
        searchHitsSubLiveData.put(subID, liveData);
        CompletableFuture.runAsync(() -> liveData.postValue(searchHits(queryString)))
                .handle(Util::printFutureError);
        return liveData;
    }

    public void searchHitsUnsubscribe(String subID) {
        searchHitsSubQueryStrings.remove(subID);
        searchHitsSubLiveData.remove(subID);
    }

    public void updateSearchHits() {
        searchHitsSubQueryStrings.keySet()
                .forEach(subID -> CompletableFuture.runAsync(() ->
                        searchHitsSubLiveData.get(subID).postValue(
                                searchHits(searchHitsSubQueryStrings.get(subID))
                        )
                ).handle(Util::printFutureError));
    }

    public int searchHits(String queryString) {
        TopDocs topDocs = search(queryString, Integer.MAX_VALUE);
        return topDocs == null ? -1 : topDocs.totalHits;
    }

    public LiveData<Set<String>> searchFieldValuesSubscribe(String subID,
                                                            String queryString,
                                                            String field) {
        MutableLiveData<Set<String>> liveData = new MutableLiveData<>();
        searchFieldValuesSubQueryStrings.put(subID, queryString);
        searchFieldValuesSubFields.put(subID, field);
        searchFieldValuesSubLiveData.put(subID, liveData);
        CompletableFuture.runAsync(() -> liveData.postValue(searchFieldValues(queryString, field)))
                .handle(Util::printFutureError);
        return liveData;
    }

    public void searchFieldValuesUnsubscribe(String subID) {
        searchFieldValuesSubQueryStrings.remove(subID);
        searchFieldValuesSubFields.remove(subID);
        searchFieldValuesSubLiveData.remove(subID);
    }

    public void updateSearchFieldValues() {
        searchFieldValuesSubQueryStrings.keySet()
                .forEach(subID -> CompletableFuture.runAsync(() ->
                        searchFieldValuesSubLiveData.get(subID).postValue(
                                searchFieldValues(
                                        searchFieldValuesSubQueryStrings.get(subID),
                                        searchFieldValuesSubFields.get(subID)
                                )
                        )
                ).handle(Util::printFutureError));
    }

    public Set<String> searchFieldValues(String queryString, String field) {
        TopDocs topDocs = search(queryString, Integer.MAX_VALUE);
        if (topDocs == null) {
            return Collections.emptySet();
        }
        HashSet<String> values = new HashSet<>();
        for (ScoreDoc sc: topDocs.scoreDocs) {
            Document doc = getDocument(sc);
            if (doc != null) {
                values.add(doc.getField(field).stringValue());
            }
        }
        return values;
    }

    public Document getDocument(ScoreDoc sd) {
        if (indexSearcher == null) {
            return null;
        }
        if (sd.doc < 0 || sd.doc >= indexSearcher.getIndexReader().maxDoc()) {
            return null;
        }
        try {
            return indexSearcher.doc(sd.doc);
        } catch (IOException e) {
            Log.e(LC, e.getMessage());
        }
        return null;
    }

    public void onChange() {
        setupIndexReader();
        updateSearchFieldValues();
        updateSearchHits();
    }
}
