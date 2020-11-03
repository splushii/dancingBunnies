package se.splushii.dancingbunnies.musiclibrary;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;

import androidx.annotation.NonNull;

public class QueryTree extends QueryNode implements Iterable<QueryNode> {
    private static final String JSON_KEY_OP = "op";
    private static final String JSON_KEY_CHILDREN = "children";

    @NonNull
    @Override
    public Iterator<QueryNode> iterator() {
        return Collections.unmodifiableList(children).iterator();
    }

    public enum Op {
        AND,
        OR
    }
    private Op operator;
    private final ArrayList<QueryNode> children;

    private QueryTree(QueryTree source) {
        super(source);
        this.operator = source.operator;
        children = new ArrayList<>();
        for (QueryNode child: source.children) {
            addChild(child.deepCopy());
        }
    }

    public QueryTree(Op operator, boolean negate) {
        super(QueryNode.JSON_VALUE_NODE_TYPE_TREE, negate);
        this.operator = operator;
        children = new ArrayList<>();
    }

    QueryTree(JSONObject jsonRoot) {
        super(jsonRoot);
        operator = Op.AND;
        children = new ArrayList<>();
        try {
            operator = Op.valueOf(jsonRoot.getString(JSON_KEY_OP));
            JSONArray jsonChildren = jsonRoot.getJSONArray(JSON_KEY_CHILDREN);
            for (int i = 0; i < jsonChildren.length(); i++) {
                children.add(QueryNode.fromJSON(jsonChildren.getJSONObject(i)));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void addChild(QueryNode node) {
        children.add(node);
    }

    public void removeChild(QueryNode node) {
        children.remove(node);
    }

    public void setOperator(Op op) {
        operator = op;
    }

    public Op getOperator() {
        return operator;
    }

    public QueryTree deepCopy() {
        return new QueryTree(this);
    }

    @Override
    public JSONObject toJSON() {
        JSONObject jsonRoot = super.toJSON();
        try {
            jsonRoot.put(JSON_KEY_OP, operator.name());
            JSONArray jsonChildren = new JSONArray();
            for (QueryNode node: children) {
                if (node != null) {
                    jsonChildren.put(node.toJSON());
                }
            }
            jsonRoot.put(JSON_KEY_CHILDREN, jsonChildren);
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
        QueryTree queryTree;
        if (operator == Op.AND) {
            queryTree = this;
        } else {
            queryTree = new QueryTree(Op.AND, false);
            queryTree.addChild(this);
        }
        queryTree.addChild(new QueryLeaf(entryID.type, entryID.id));
        return queryTree;
    }

    @Override
    public HashSet<String> getKeys() {
        HashSet<String> keys = new HashSet<>();
        for (QueryNode node: children) {
            if (node != null) {
                keys.addAll(node.getKeys());
            }
        }
        return keys;
    }
}
