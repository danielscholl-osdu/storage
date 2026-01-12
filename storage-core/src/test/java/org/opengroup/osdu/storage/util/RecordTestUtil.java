package org.opengroup.osdu.storage.util;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import org.opengroup.osdu.core.common.model.storage.Record;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RecordTestUtil {

    private RecordTestUtil() {
    }

    // Create a record from another record, copying all fields except the id using all-arguments constructor
    public static Record ofOtherAndCollaboration(Record other, UUID collaborationId) {
        return new Record(
                collaborationId.toString()+ other.getId() ,
                other.getVersion(),
                other.getKind(),
                other.getAcl(),
                other.getLegal(),
                cloneData(other.getData()),
                other.getAncestry(),
                cloneMeta(other.getMeta()),
                cloneTags(other.getTags()),
                other.getCreateUser(),
                other.getCreateTime(),
                other.getModifyUser(),
                other.getModifyTime()
        );
    }


    private static Map<String, String> cloneTags(Map<String, String> tags) {
        if (tags == null) {
            return null;
        }
        return new HashMap<>(tags);
    }

    private static Map<String, Object> cloneData(Map<String, Object> data) {
        if (data == null) {
            return null;
        }
        String json = new Gson().toJson(data);
        return new Gson().fromJson(json, new TypeToken<Map<String, Object>>() {
        }.getType());
    }

    private static Map<String, Object>[] cloneMeta(Map<String, Object>[] meta) {
        if (meta == null) {
            return null;
        }
        String json = new Gson().toJson(meta);
        return new Gson().fromJson(json, new TypeToken<Map<String, Object>>() {
        }.getType());
    }


}
