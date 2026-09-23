// Copyright 2017-2019, Schlumberger
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

package org.opengroup.osdu.storage.util;

import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.apache.hc.client5.http.HttpHostConnectException;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import static org.apache.http.HttpStatus.SC_SERVICE_UNAVAILABLE;
import static org.apache.hc.core5.http.HttpStatus.SC_GATEWAY_TIMEOUT;
import static org.apache.hc.core5.http.HttpStatus.SC_BAD_GATEWAY;
import static org.apache.hc.core5.http.HttpStatus.SC_TOO_MANY_REQUESTS;

public final class RetryPolicy {
    public static RetryConfig httpRetryConfig(int maxAttempts, int backoffInitialIntervalInSecs, int backOffMultiplier) {
                // List of status codes to retry on
        List<Integer> retryStatusCodes = List.of(
                SC_TOO_MANY_REQUESTS, // 429
                SC_SERVICE_UNAVAILABLE, // 503
                SC_GATEWAY_TIMEOUT, // 504
                SC_BAD_GATEWAY // 502
        );
        return RetryConfig.custom()
                .maxAttempts(maxAttempts)
                // Exponential backoff with jitter
                .intervalFunction(
                        IntervalFunction.ofExponentialRandomBackoff(
                                Duration.ofMillis(backoffInitialIntervalInSecs),
                                backOffMultiplier
                        ))
                // Retry on network exceptions
                .retryExceptions(
                        SocketException.class,
                        SocketTimeoutException.class,
                        ConnectException.class,
                        UnknownHostException.class,
                        HttpHostConnectException.class)
                .retryOnResult(response -> {
                    if (response instanceof CloseableHttpResponse httpResponse) {
                        int statusCode = httpResponse.getCode();
                        return retryStatusCodes.contains(statusCode);
                    }
                    return false;
                })
                .build();
    }

    // Use for logging retries.
    public static void logRetryEvents(Retry retry){
        AtomicLong lastRetryTime = new AtomicLong(System.currentTimeMillis());

        retry.getEventPublisher().onRetry(event -> {
            long now = System.currentTimeMillis();
            long lastTime = lastRetryTime.getAndSet(now);
            long timeElapsed = (lastTime == 0) ? 0 : (now - lastTime);

            System.out.println("Retry Attempt: " + event.getNumberOfRetryAttempts() +
                    " due to: " + event.getLastThrowable() +
                    " | Time since last attempt: " + timeElapsed + "ms");
        });
    }
}
