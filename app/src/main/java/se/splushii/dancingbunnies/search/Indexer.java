package se.splushii.dancingbunnies.search;

import android.content.Context;
import android.util.Log;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import androidx.core.util.Consumer;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.util.Util;

// TODO: Properly handle exceptions
public class Indexer {
    private static final String LC = Util.getLogContext(Indexer.class);

    static final Version LUCENE_VERSION = Version.LUCENE_48;

    static final String FIELD_ARTIST = "artist";
    static final String FIELD_ALBUM = "album";
    static final String FIELD_TITLE = "title";

    private static final String LUCENE_INDEX_PATH = "lucene_index";

    private static volatile Indexer instance;

    private File indexDirectoryPath;
    private FSDirectory indexDirectory;
    private IndexWriter indexWriter;

    public static synchronized Indexer getInstance(Context context) {
        if (instance == null) {
            instance = new Indexer(context.getApplicationContext());
        }
        return instance;
    }

    private Indexer(Context context) {
        indexDirectoryPath = prepareIndexPath(context.getFilesDir());
    }

    public synchronized boolean initialize(long writeLockTimeout) {
        if (indexDirectory != null && indexWriter != null) {
            return true;
        }
        try {
            indexDirectory = FSDirectory.open(indexDirectoryPath);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        Analyzer analyzer = new StandardAnalyzer(LUCENE_VERSION);
        IndexWriterConfig config = new IndexWriterConfig(LUCENE_VERSION, analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        config.setWriteLockTimeout(writeLockTimeout);
        try {
            indexWriter = new IndexWriter(indexDirectory, config);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public static File prepareIndexPath(File filesDir) {
        File currentIndexPath = new File(filesDir, LUCENE_INDEX_PATH);
        if (!currentIndexPath.isDirectory()) {
            if (!currentIndexPath.mkdirs()) {
                Log.w(LC, "Could not create lucene index dir " + currentIndexPath.toPath());
            }
        }
        return currentIndexPath;
    }

    public synchronized int indexSongs(List<Meta> meta, Consumer<Integer> progress) {
        if (indexWriter == null) {
            Log.e(LC, "indexSong: indexWriter is null");
            return 0;
        }
        List<Document> docs = meta.stream()
                .map(this::prepareSongForIndex)
                .collect(Collectors.toList());
        int count = 0;
        progress.accept(count);
        for (Document doc: docs) {
            try {
                indexWriter.addDocument(doc);
                count++;
                progress.accept(count);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        Searcher.getInstance().onChange();
        return count;
    }

    private synchronized Document prepareSongForIndex(Meta meta) {
        Document doc = new Document();
        doc.add(new TextField(Meta.FIELD_SPECIAL_ENTRY_SRC, meta.entryID.src, Field.Store.YES));
        doc.add(new TextField(Meta.FIELD_SPECIAL_ENTRY_ID_TRACK, meta.entryID.id, Field.Store.YES));
        for (String key: meta.keySet()) {
            float boost;
            List<Field> fields = Collections.emptyList();
            switch (key) {
                case Meta.FIELD_TITLE:
                    boost = 4.7f;
                    break;
                case Meta.FIELD_ARTIST:
                    boost = 2.3f;
                    break;
                case Meta.FIELD_ALBUM:
                    boost = 1.1f;
                    break;
                default:
                    boost = 1.0f;
            }
            switch (Meta.getType(key)) {
                case STRING:
                    fields = meta.getStrings(key).stream()
                            .map(s -> new TextField(key, s, Field.Store.NO))
                            .collect(Collectors.toList());
                    break;
                case DOUBLE:
                    fields = meta.getDoubles(key).stream()
                            .map(String::valueOf)
                            .map(s -> new TextField(key, s, Field.Store.NO))
                            .collect(Collectors.toList());
                    break;
                case LONG:
                    fields = meta.getLongs(key).stream()
                            .map(String::valueOf)
                            .map(s -> new TextField(key, s, Field.Store.NO))
                            .collect(Collectors.toList());
                    break;
            }
            fields.forEach(f -> {
                f.setBoost(boost);
                doc.add(f);
            });
        }
        return doc;
    }

    public synchronized void removeSongs(String src) {
        if (indexWriter == null) {
            Log.e(LC, "removeSongs: indexWriter is null");
            return;
        }
        QueryParser queryParser = Searcher.getQueryParser();
        Query query;
        try {
            query = queryParser.parse(Meta.FIELD_SPECIAL_ENTRY_SRC + ": \"" + src + "\"");
        } catch (ParseException e) {
            Log.e(LC, e.getMessage());
            return;
        }
        try {
            indexWriter.deleteDocuments(query);
        } catch (IOException e) {
            Log.e(LC, e.getMessage());
        }
        Searcher.getInstance().onChange();
    }

    public synchronized void close() {
        if (indexWriter == null) {
            Log.d(LC, "close: indexWriter is null");
            return;
        }
        try {
            indexWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            indexWriter = null;
            try {
                if (IndexWriter.isLocked(indexDirectory)) {
                    IndexWriter.unlock(indexDirectory);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        Searcher.getInstance().onChange();
    }
}
