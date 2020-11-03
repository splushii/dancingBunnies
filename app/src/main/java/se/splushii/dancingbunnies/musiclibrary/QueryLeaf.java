package se.splushii.dancingbunnies.musiclibrary;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public class QueryLeaf extends QueryNode {
    private static final String JSON_KEY_KEY = "key";
    private static final String JSON_KEY_OP = "op";
    private static final String JSON_KEY_VALUE = "value";

    public enum Op {
        EQUALS,
        LIKE,
        LESS,
        LESS_OR_EQUALS,
        GREATER,
        GREATER_OR_EQUALS,
        EXISTS
    }
    private String key;
    private Op operator;
    private String value;

    private QueryLeaf(QueryLeaf source) {
        super(source);
        init(source.key, source.operator, source.value);
    }

    QueryLeaf(String key, String value) {
        super(QueryNode.JSON_VALUE_NODE_TYPE_LEAF, false);
        init(key, Op.EQUALS, value);
    }

    public QueryLeaf(String key, Op operator, String value, boolean negate) {
        super(QueryNode.JSON_VALUE_NODE_TYPE_LEAF, negate);
        init(key, operator, value);
    }

    private void init(String key, Op op, String value) {
        this.key = key;
        setOperatorAndValue(op, value);
    }

    QueryLeaf(JSONObject jsonRoot) {
        super(jsonRoot);
        try {
            key = jsonRoot.getString(JSON_KEY_KEY);
            setOperatorAndValue(
                    Op.valueOf(jsonRoot.getString(JSON_KEY_OP)),
                    jsonRoot.has(JSON_KEY_VALUE) ? jsonRoot.getString(JSON_KEY_VALUE) : null
            );
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void setOperatorAndValue(Op operator, String value) {
        this.operator = operator;
        if (Op.EXISTS.equals(operator)) {
            this.value = null;
            return;
        }
        this.value = value;
    }

    public void setOperator(Op op) {
        setOperatorAndValue(op, value);
    }

    public Op getOperator() {
        return operator;
    }

    public String getKey() {
        return key;
    }

    public void setValue(String value) {
        setOperatorAndValue(operator, value);
    }

    public String getValue() {
        return value;
    }

    @Override
    public QueryLeaf deepCopy() {
        return new QueryLeaf(this);
    }

    @Override
    public JSONObject toJSON() {
        JSONObject jsonRoot = super.toJSON();
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
    public QueryNode withEntryID(EntryID entryID) {
        if (entryID == null || entryID.isUnknown()) {
            return this;
        }
        QueryTree queryTree = new QueryTree(
                QueryTree.Op.AND,
                false
        );
        queryTree.addChild(this);
        queryTree.addChild(new QueryLeaf(entryID.type, entryID.id));
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
            case EXISTS:
                return "IS NOT"; // IS NOT NULL
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
            case EXISTS:
                return "exists";
        }
    }

    public static List<String> getDisplayableOps(String key) {
        return getOps(key).stream()
                .map(QueryLeaf::getDisplayableOp)
                .collect(Collectors.toList());
    }

    public static List<Op> getOps(String key) {
        switch (Meta.getType(key)) {
            default:
            case STRING:
                return Arrays.asList(
                        Op.EQUALS,
                        Op.LIKE,
                        Op.EXISTS
                );
            case DOUBLE:
            case LONG:
                return Arrays.asList(
                        Op.EQUALS,
                        Op.LESS,
                        Op.LESS_OR_EQUALS,
                        Op.GREATER,
                        Op.GREATER_OR_EQUALS,
                        Op.EXISTS
                );
        }
    }
}
