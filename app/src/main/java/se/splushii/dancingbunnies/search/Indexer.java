package se.splushii.dancingbunnies.search;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.util.Util;

// TODO: Properly handle exceptions
public class Indexer {
    private static final String LC = Util.getLogContext(Indexer.class);

    public static final Version LUCENE_VERSION = Version.LUCENE_48;

    private static final String FIELD_API = "src";
    private static final String FIELD_MEDIA_ID = "id";
    static final String FIELD_ARTIST = "artist";
    static final String FIELD_ALBUM = "album";
    static final String FIELD_TITLE = "title";

    public static final HashMap<String, String> meta2fieldNameMap;

    static {
        meta2fieldNameMap = new HashMap<>();
        meta2fieldNameMap.put(Meta.METADATA_KEY_API, FIELD_API);
        meta2fieldNameMap.put(Meta.METADATA_KEY_MEDIA_ID, FIELD_MEDIA_ID);
        meta2fieldNameMap.put(Meta.METADATA_KEY_ARTIST, FIELD_ARTIST);
        meta2fieldNameMap.put(Meta.METADATA_KEY_ALBUM, FIELD_ALBUM);
        meta2fieldNameMap.put(Meta.METADATA_KEY_TITLE, FIELD_TITLE);
    }

    private IndexWriter indexWriter;
    public Indexer(File indexDirectoryPath) {
        Directory indexDirectory;
        try {
            indexDirectory = FSDirectory.open(indexDirectoryPath);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        Analyzer analyzer = new StandardAnalyzer(LUCENE_VERSION);
        IndexWriterConfig config = new IndexWriterConfig(LUCENE_VERSION, analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE); // TODO: Use CREATE_APPEND
        try {
            indexWriter = new IndexWriter(indexDirectory, config);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int indexSong(Meta meta) {
        Document doc = new Document();
        for (String key: meta.keySet()) {
            if (meta2fieldNameMap.containsKey(key)) {
                String fieldName = meta2fieldNameMap.get(key);
                // TODO: FIXME: Not everything is a string...
                String fieldValue = meta.getString(key);
                Field.Store store = key.equals(Meta.METADATA_KEY_MEDIA_ID)
                        || key.equals(Meta.METADATA_KEY_API)
                        || key.equals(Meta.METADATA_KEY_TITLE) ?
                        Field.Store.YES : Field.Store.NO;
                Field field = new TextField(fieldName, fieldValue, store);
                doc.add(field);
            }
        }
        try {
            indexWriter.addDocument(doc);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return indexWriter.numDocs();
    }

    public void close() {
        try {
            indexWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
