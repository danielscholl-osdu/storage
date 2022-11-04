package org.opengroup.osdu.storage.util;

import com.google.gson.Gson;
import org.opengroup.osdu.core.common.util.Crc32c;

import java.nio.charset.StandardCharsets;

import static org.apache.commons.codec.binary.Base64.encodeBase64;

public class CrcHashGenerator {

    public static String getHash(Object data) {
        Gson gson = new Gson();
        Crc32c checksumGenerator = new Crc32c();
        String newRecordStr = gson.toJson(data);
        byte[] bytes = newRecordStr.getBytes(StandardCharsets.UTF_8);
        checksumGenerator.update(bytes, 0, bytes.length);
        bytes = checksumGenerator.getValueAsBytes();
        return new String(encodeBase64(bytes));
    }

}
