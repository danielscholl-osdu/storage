package org.opengroup.osdu.storage.provider.azure.pubsub;

import com.google.gson.Gson;
import com.microsoft.azure.servicebus.Message;
import com.microsoft.azure.servicebus.MessageBody;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.storage.dto.ReplayMessage;
import org.opengroup.osdu.storage.provider.azure.util.MDCContextMap;
import org.opengroup.osdu.storage.service.replay.IReplayService;

import java.util.HashMap;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ReplayMessageHandlerTest {

    @InjectMocks
    private ReplayMessageHandler replayMessageHandler;

    @Mock
    private IReplayService replayService;

    @Mock
    private DpsHeaders dpsHeaders;

    @Mock
    private MDCContextMap mdcContextMap;

    private ReplayMessage replayMessage = ReplayMessage.builder().headers(new HashMap<>()).build();

    @Test
    public void shouldInvokeProcessReplayMessage() {

        when(mdcContextMap.getContextMap(any(), any())).thenReturn(new HashMap<>());
        Message message = new Message();
        message.setMessageBody(MessageBody.fromValueData(new Gson().toJson(replayMessage)));
        replayMessageHandler.handle(message);
        verify(replayService, times(1)).processReplayMessage(any());
    }

    @Test
    public void shouldInvokeProcessFailure() {

        Message message = new Message();
        message.setMessageBody(MessageBody.fromValueData(new Gson().toJson(replayMessage)));
        replayMessageHandler.handleFailure(message);
        verify(replayService, times(1)).processFailure(any());
    }
}
