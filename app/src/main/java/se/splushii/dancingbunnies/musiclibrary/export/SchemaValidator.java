package se.splushii.dancingbunnies.musiclibrary.export;

import android.content.Context;
import android.util.Log;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.Format;
import com.networknt.schema.JsonMetaSchema;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.util.Util;

public class SchemaValidator {
    private static final String LC = Util.getLogContext(SchemaValidator.class);
    // TODO: Move this to another class
    public static final String PLAYLIST_SUFFIX = "yaml";
    public static final String PLAYLIST_ENTRY_TYPE_TRACK = "track";
    public static final String PLAYLIST_ENTRY_TYPE_PLAYLIST = "playlist";

    public static boolean validatePlaylist(Context context, InputStream inputStream) {
        ObjectMapper objMapper = new ObjectMapper(new YAMLFactory());
        return validate(objMapper, getPlaylistSchema(context, objMapper), inputStream);
    }

    private static JsonSchema getPlaylistSchema(Context context, ObjectMapper objMapper) {
        String metaSchemaURI = "https://dancingbunnies.splushii.se/meta-schema/2019-09/dancingbunnies";
        final JsonMetaSchema metaSchema = JsonMetaSchema.builder(metaSchemaURI, JsonMetaSchema.getV201909())
                .addFormat(new Format() {
                    private String value;

                    @Override
                    public String getName() {
                        return "src";
                    }

                    @Override
                    public boolean matches(String value) {
                        this.value = value;
                        return MusicLibraryService.matchAPISourceSyntax(value);
                    }

                    @Override
                    public String getErrorMessageDescription() {
                        return ". Value \"" + value + "\" does not conform to regex: "
                                + MusicLibraryService.API_SRC_REGEX;
                    }
                })
                .build();
        final JsonSchemaFactory schemaFactory = JsonSchemaFactory
                .builder(JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V201909))
                .addMetaSchema(metaSchema)
                .objectMapper(objMapper)
                .build();
        InputStream schemaStream = context.getResources().openRawResource(R.raw.playlist_schema);
        return schemaFactory.getSchema(schemaStream);
    }

    private static boolean validate(ObjectMapper objMapper,
                                    JsonSchema schema,
                                    InputStream inputStream) {
        JsonNode jsonNode;
        try {
            jsonNode = objMapper.readTree(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(LC, "Validation failed: Could not parse input");
            return false;
        }
        Set<ValidationMessage> validationMsgs = schema.validate(jsonNode);
        if (validationMsgs.isEmpty()) {
            Log.d(LC, "Validation succeeded");
            return true;
        } else {
            Log.e(LC, "Validation failed");
            for (ValidationMessage msg : validationMsgs) {
                Log.e(LC, msg.toString());
            }
            return false;
        }
    }
}
