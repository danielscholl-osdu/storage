package org.opengroup.osdu.storage.provider.aws.config;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.concurrent.ExecutorService;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {ReplayBatchConfig.class})
@TestPropertySource(properties = {
        "feature.replay.enabled=true",
        "replay.batch.size=100",
        "replay.batch.parallelism=8"
})
public class ReplayBatchConfigTest {

    @Autowired
    private ReplayBatchConfig replayBatchConfig;

    @Test
    public void testConfigProperties() {
        assertEquals(100, replayBatchConfig.getBatchSize());
        assertEquals(8, replayBatchConfig.getParallelism());
    }

    @Test
    public void testReplayExecutorService() {
        ExecutorService executorService = replayBatchConfig.replayExecutorService();
        assertNotNull(executorService);
        assertFalse(executorService.isShutdown());
        
        // Clean up
        executorService.shutdown();
    }
}
