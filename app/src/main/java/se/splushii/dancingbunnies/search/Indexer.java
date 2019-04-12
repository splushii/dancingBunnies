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
            switch (Meta.getType(key)) {
                case STRING:
                    meta.getStrings(key).forEach(s ->
                            doc.add(new TextField(key, s, Field.Store.NO))
                    );
                    break;
                case DOUBLE:
                    meta.getDoubles(key).stream()
                            .map(String::valueOf)
                            .forEach(s -> doc.add(new TextField(key, s, Field.Store.NO)));
                    break;
                case LONG:
                    meta.getLongs(key).stream()
                            .map(String::valueOf)
                            .forEach(s -> doc.add(new TextField(key, s, Field.Store.NO)));
                    break;
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
