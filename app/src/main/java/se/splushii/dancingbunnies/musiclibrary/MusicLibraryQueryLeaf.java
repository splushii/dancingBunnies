package se.splushii.dancingbunnies.musiclibrary;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public class MusicLibraryQueryLeaf extends MusicLibraryQueryNode {
    private static final String JSON_KEY_KEY = "key";
    private static final String JSON_KEY_OP = "op";
    static final String JSON_KEY_VALUE = "value";

    public enum Op {
        EQUALS,
        LIKE,
        LESS,
        LESS_OR_EQUALS,
        GREATER,
        GREATER_OR_EQUALS
    }
    private String key;
    private Op operator;
    private String value;

    public MusicLibraryQueryLeaf(String key, String value) {
        this.key = key;
        operator = Op.EQUALS;
        this.value = value;
    }

    public MusicLibraryQueryLeaf(String key, Op operator, String value) {
        this.key = key;
        this.operator = operator;
        this.value = value;
    }

    public MusicLibraryQueryLeaf(JSONObject jsonRoot) {
        try {
            key = jsonRoot.getString(JSON_KEY_KEY);
            operator = Op.valueOf(jsonRoot.getString(JSON_KEY_OP));
            value = jsonRoot.getString(JSON_KEY_VALUE);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void setOperator(Op op) {
        operator = op;
    }

    public Op getOperator() {
        return operator;
    }

    public String getKey() {
        return key;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public MusicLibraryQueryLeaf deepCopy() {
        MusicLibraryQueryLeaf copy = new MusicLibraryQueryLeaf(key, value);
        copy.setOperator(operator);
        return copy;
    }

    @Override
    public JSONObject toJSON() {
        JSONObject jsonRoot = new JSONObject();
        try {
            jsonRoot.put(JSON_KEY_KEY, key);
            jsonRoot.put(JSON_KEY_OP, operator.name());
            jsonRoot.put(JSON_KEY_VALUE, value);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonRoot;
    }

    @Override
    public MusicLibraryQueryNode withEntryID(EntryID entryID) {
        if (entryID.isUnknown()) {
            return this;
        }
        MusicLibraryQueryTree queryTree = new MusicLibraryQueryTree(MusicLibraryQueryTree.Op.AND);
        queryTree.addChild(this);
        queryTree.addChild(new MusicLibraryQueryLeaf(entryID.type, entryID.id));
        return queryTree;
    }

    @Override
    public HashSet<String> getKeys() {
        return new HashSet<>(Collections.singleton(key));
    }

    public String getSQLOp() {
        switch (operator) {
            case EQUALS:
                return "==";
            case LIKE:
                return "LIKE";
            case LESS:
                return "<";
            case LESS_OR_EQUALS:
                return "<=";
            case GREATER:
                return ">";
            case GREATER_OR_EQUALS:
                return ">=";
            default:
                return null;
        }
    }

    public static String getDisplayableOp(Op op) {
        switch (op) {
            default:
            case EQUALS:
                return "=";
            case LIKE:
                return "~";
            case LESS:
                return "<";
            case LESS_OR_EQUALS:
                return "<=";
            case GREATER:
                return ">";
            case GREATER_OR_EQUALS:
                return ">=";
        }
    }

    public static List<String> getDisplayableOps(String key) {
        return getOps(key).stream()
                .map(MusicLibraryQueryLeaf::getDisplayableOp)
                .collect(Collectors.toList());
    }

    public static List<Op> getOps(String key) {
        switch (Meta.getType(key)) {
            default:
            case STRING:
                return Arrays.asList(
                        Op.EQUALS,
                        Op.LIKE
                );
            case DOUBLE:
            case LONG:
                return Arrays.asList(
                        Op.EQUALS,
                        Op.LESS,
                        Op.LESS_OR_EQUALS,
                        Op.GREATER,
                        Op.GREATER_OR_EQUALS
                );
        }
    }
}
