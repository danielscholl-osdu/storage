/*
 * Copyright © Amazon Web Services
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengroup.osdu.storage.provider.aws.exception;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import java.lang.reflect.Field;

/**
 * Unit tests for {@link ReplayMessageHandlerException}.
 */
public class ReplayMessageHandlerExceptionTest {

    private static final String ERROR_MESSAGE = "Test error message";
    
    /**
     * Test the constructor that takes only a message.
     */
    @Test
    public void testConstructorWithMessage() {
        ReplayMessageHandlerException exception = new ReplayMessageHandlerException(ERROR_MESSAGE);
        
        assertEquals("Exception message should match the provided message", 
                ERROR_MESSAGE, exception.getMessage());
        assertNull("Cause should be null", exception.getCause());
    }
    
    /**
     * Test the constructor that takes a message and a cause.
     */
    @Test
    public void testConstructorWithMessageAndCause() {
        Throwable cause = new RuntimeException("Original cause");
        ReplayMessageHandlerException exception = new ReplayMessageHandlerException(ERROR_MESSAGE, cause);
        
        assertEquals("Exception message should match the provided message", 
                ERROR_MESSAGE, exception.getMessage());
        assertSame("Cause should be the same as provided", cause, exception.getCause());
    }
    
    /**
     * Test the constructor that takes only a cause.
     */
    @Test
    public void testConstructorWithCause() {
        Throwable cause = new RuntimeException("Original cause");
        ReplayMessageHandlerException exception = new ReplayMessageHandlerException(cause);
        
        assertEquals("Exception message should match the cause's toString", 
                cause.toString(), exception.getMessage());
        assertSame("Cause should be the same as provided", cause, exception.getCause());
    }
    
    /**
     * Test that the exception properly extends Exception.
     */
    @Test
    public void testExceptionInheritance() {
        ReplayMessageHandlerException exception = new ReplayMessageHandlerException(ERROR_MESSAGE);
        
        assertEquals("Exception should be an instance of Exception", 
                Exception.class, exception.getClass().getSuperclass());
    }
    
    /**
     * Test serialization constant is defined.
     */
    @Test
    public void testSerialVersionUID() throws NoSuchFieldException, IllegalAccessException {
        Field field = ReplayMessageHandlerException.class.getDeclaredField("serialVersionUID");
        field.setAccessible(true);
        long serialVersionUID = (long) field.get(null);
        assertEquals("SerialVersionUID should be 1L", 1L, serialVersionUID);
    }
}
