// Copyright © Amazon Web Services
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.opengroup.osdu.storage.provider.aws.exception;

/**
 * Custom exception for handling errors in the ReplayMessageHandler.
 * This exception is thrown when there are issues with publishing or processing replay messages.
 */
public class ReplayMessageHandlerException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new ReplayMessageHandlerException with the specified detail message.
     *
     * @param message the detail message
     */
    public ReplayMessageHandlerException(String message) {
        super(message);
    }

    /**
     * Constructs a new ReplayMessageHandlerException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public ReplayMessageHandlerException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new ReplayMessageHandlerException with the specified cause.
     *
     * @param cause the cause of the exception
     */
    public ReplayMessageHandlerException(Throwable cause) {
        super(cause);
    }
}
