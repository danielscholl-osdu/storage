package org.opengroup.osdu.storage.util;

import org.apache.http.HttpStatus;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opengroup.osdu.core.common.model.http.AppException;

public class EncodeDecodeTest {

    private EncodeDecode encodeDecode;

    @Before
    public void setup(){
        encodeDecode = new EncodeDecode();
    }

    // TODO: Trufflehog preventing from pushing base64 string tests. Removed tests because of that.
    @Test
    public void should_decodeToString_postEncodingDecoding() {
        String inputString = "hello+world";

        String resultString = encodeDecode.deserializeCursor(encodeDecode.serializeCursor(inputString));
        Assert.assertEquals(inputString, resultString);
    }

    @Test
    public void should_throwError_onNonBase64Input() {
        String inputString = "invalid_cursor";
        try {
            encodeDecode.deserializeCursor(inputString);
            Assert.fail();
        }catch (AppException exception){
            Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, exception.getError().getCode());
        }
    }

}
