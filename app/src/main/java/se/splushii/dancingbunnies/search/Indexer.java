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
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import se.splushii.dancingbunnies.musiclibrary.Meta;

// TODO: Properly handle exceptions
public class Indexer {
    static final Version LUCENE_VERSION = Version.LUCENE_48;

    static final String FIELD_ARTIST = "artist";
    static final String FIELD_ALBUM = "album";
    static final String FIELD_TITLE = "title";

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
        doc.add(new TextField(Meta.FIELD_SPECIAL_MEDIA_SRC, meta.entryID.src, Field.Store.YES));
        doc.add(new TextField(Meta.FIELD_SPECIAL_MEDIA_ID, meta.entryID.id, Field.Store.YES));
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
