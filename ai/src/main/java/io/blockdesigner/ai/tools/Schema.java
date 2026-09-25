package io.blockdesigner.ai.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Tiny JSON Schema builder for tool definitions. */
final class Schema {
    static final ObjectMapper JSON = new ObjectMapper();

    private final ObjectNode root = JSON.createObjectNode().put("type", "object");
    private final ObjectNode props = root.putObject("properties");
    private final ArrayNode required = root.putArray("required");

    static Schema object() {
        return new Schema();
    }

    Schema req(String name, JsonNode schema) {
        props.set(name, schema);
        required.add(name);
        return this;
    }

    Schema opt(String name, JsonNode schema) {
        props.set(name, schema);
        return this;
    }

    JsonNode build() {
        root.put("additionalProperties", false);
        return root;
    }

    static ObjectNode str(String desc) {
        return JSON.createObjectNode().put("type", "string").put("description", desc);
    }

    static ObjectNode enumOf(String desc, String... values) {
        ObjectNode n = str(desc);
        ArrayNode e = n.putArray("enum");
        for (String v : values) e.add(v);
        return n;
    }

    static ObjectNode integer(String desc) {
        return JSON.createObjectNode().put("type", "integer").put("description", desc);
    }

    static ObjectNode bool(String desc) {
        return JSON.createObjectNode().put("type", "boolean").put("description", desc);
    }

    /** [x, y, z] world coordinates. */
    static ObjectNode pos(String desc) {
        ObjectNode n = JSON.createObjectNode().put("type", "array").put("description", desc + " as [x, y, z]");
        n.putObject("items").put("type", "integer");
        n.put("minItems", 3).put("maxItems", 3);
        return n;
    }

    static ObjectNode array(String desc, JsonNode items) {
        ObjectNode n = JSON.createObjectNode().put("type", "array").put("description", desc);
        n.set("items", items);
        return n;
    }
}
