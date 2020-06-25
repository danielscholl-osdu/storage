package org.opengroup.osdu.storage.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.jersey.api.client.ClientResponse;

public class SchemaUtil {

    protected final String schema = TenantUtils.getTenantName() + ":storage:inttest:1.0.0"
            + System.currentTimeMillis();

    public static ClientResponse create(String kind, String token) throws Exception {
        return TestUtils.send("schemas", "POST", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), SchemaUtil.validSchemaPostBody(kind), "");
    }

    public static ClientResponse delete(String kind, String token) throws Exception {
        return TestUtils.send("schemas/" + kind, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), "", "");
    }

    protected static String validSchemaPostBody(String kind) {

        JsonObject item1Ext = new JsonObject();
        item1Ext.addProperty("indexerTip", "call911");

        JsonObject item1 = new JsonObject();
        item1.addProperty("path", "name");
        item1.addProperty("kind", "string");
        item1.add("ext", item1Ext);

        JsonObject item2Ext = new JsonObject();
        item2Ext.addProperty("address.city", "this is a weird string");
        item2Ext.addProperty("address.country", "country with two letters");

        JsonObject item2 = new JsonObject();
        item2.addProperty("path", "age");
        item2.addProperty("kind", "int");
        item2.add("ext", item2Ext);

        JsonArray schemaItems = new JsonArray();
        schemaItems.add(item1);
        schemaItems.add(item2);

        JsonObject schema = new JsonObject();
        schema.addProperty("kind", kind);
        schema.add("schema", schemaItems);
        schema.add("ext", item2Ext);

        return schema.toString();
    }
}
