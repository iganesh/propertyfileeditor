package com.refinitiv.platformservices.rt.objects.chain;

import com.refinitiv.ema.access.ElementList;
import com.refinitiv.ema.access.FieldList;
import com.refinitiv.ema.access.OmmConsumer;
import com.refinitiv.ema.access.OmmConsumerClient;
import com.refinitiv.ema.access.OmmException;
import com.refinitiv.ema.access.RefreshMsg;
import com.refinitiv.ema.access.ReqMsg;
import com.refinitiv.ema.access.StatusMsg;
import com.refinitiv.ema.access.UpdateMsg;
import com.refinitiv.platformservices.rt.objects.common.Dispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecursiveChainImplTest {

    @Mock
    private OmmConsumer ommConsumer;

    @Mock
    private Dispatcher dispatcher;

    @Mock
    private ChainRecord chainRecord;

    @InjectMocks
    private RecursiveChainImpl recursiveChain;

    private static final String CHAIN_NAME = "0#TEST.CHA";
    private static final String SERVICE_NAME = "ELEKTRON_DD";

    @BeforeEach
    void setUp() {
        recursiveChain = new RecursiveChainImpl.Builder()
                .withOmmConsumer(ommConsumer)
                .withChainName(CHAIN_NAME)
                .withServiceName(SERVICE_NAME)
                .build();
        // Dispatcher is not set via Builder; it may be injected or set differently.
        // For testing, we assume Dispatcher is handled internally or via ChainRecordListener.
    }

    @Test
    void testOpen_SuccessfulSubscription() {
        // Arrange
        ArgumentCaptor<ReqMsg> reqMsgCaptor = ArgumentCaptor.forClass(ReqMsg.class);
        ArgumentCaptor<OmmConsumerClient> clientCaptor = ArgumentCaptor.forClass(OmmConsumerClient.class);

        // Act
        recursiveChain.open();

        // Assert
        verify(ommConsumer).registerClient(reqMsgCaptor.capture(), clientCaptor.capture());
        ReqMsg capturedReqMsg = reqMsgCaptor.getValue();
        assertEquals(CHAIN_NAME, capturedReqMsg.name());
        assertEquals(SERVICE_NAME, capturedReqMsg.serviceName());
        // Instead of isOpen(), verify subscription was attempted
        verify(ommConsumer, times(1)).registerClient(any(), any());
    }

    @Test
    void testOpen_ThrowsOmmException() {
        // Arrange
        doThrow(new OmmException("Connection error")).when(ommConsumer).registerClient(any(ReqMsg.class), any(OmmConsumerClient.class));

        // Act & Assert
        assertThrows(OmmException.class, () -> recursiveChain.open());
        // Verify no subscription was successful
        verify(ommConsumer, times(1)).registerClient(any(), any());
    }

    @Test
    void testOnRefreshMsg_ValidChainData() {
        // Arrange
        RefreshMsg refreshMsg = mock(RefreshMsg.class);
        FieldList fieldList = mock(FieldList.class);
        when(refreshMsg.payload()).thenReturn(fieldList);
        when(fieldList.has(anyInt())).thenReturn(true);
        when(fieldList.getEntry(anyInt())).thenReturn(mock(com.refinitiv.ema.access.FieldEntry.class));
        List<String> constituents = Arrays.asList("RIC1", "RIC2");
        when(chainRecord.getConstituents()).thenReturn(constituents);
        recursiveChain.open();

        // Act
        recursiveChain.onRefreshMsg(refreshMsg, ommConsumer);

        // Assert
        verify(chainRecord).update(fieldList);
        verify(dispatcher).dispatchChainUpdatedEvent(recursiveChain);
        assertEquals(constituents, recursiveChain.getConstituents());
    }

    @Test
    void testOnUpdateMsg_ValidUpdate() {
        // Arrange
        UpdateMsg updateMsg = mock(UpdateMsg.class);
        FieldList fieldList = mock(FieldList.class);
        when(updateMsg.payload()).thenReturn(fieldList);
        when(fieldList.has(anyInt())).thenReturn(true);
        recursiveChain.open();

        // Act
        recursiveChain.onUpdateMsg(updateMsg, ommConsumer);

        // Assert
        verify(chainRecord).update(fieldList);
        verify(dispatcher).dispatchChainUpdatedEvent(recursiveChain);
    }

    @Test
    void testOnStatusMsg_ChainClosed() {
        // Arrange
        StatusMsg statusMsg = mock(StatusMsg.class);
        when(statusMsg.state()).thenReturn(mock(com.refinitiv.ema.access.OmmState.class));
        when(statusMsg.state().streamState()).thenReturn(com.refinitiv.ema.access.OmmState.StreamState.CLOSED);
        recursiveChain.open();

        // Act
        recursiveChain.onStatusMsg(statusMsg, ommConsumer);

        // Assert
        verify(dispatcher).dispatchChainClosedEvent(recursiveChain);
        // Verify unregistration attempt instead of isOpen()
        verify(ommConsumer, atLeastOnce()).unregister(anyLong());
    }

    @Test
    void testClose_UnsubscribesSuccessfully() {
        // Arrange
        recursiveChain.open();

        // Act
        recursiveChain.close();

        // Assert
        verify(ommConsumer).unregister(anyLong());
        // Verify unregistration instead of isOpen()
        verify(ommConsumer, atLeastOnce()).unregister(anyLong());
    }

    @Test
    void testGetConstituents_EmptyWhenNotUpdated() {
        // Arrange
        when(chainRecord.getConstituents()).thenReturn(Arrays.asList());

        // Act
        List<String> constituents = recursiveChain.getConstituents();

        // Assert
        assertTrue(constituents.isEmpty());
    }

    @Test
    void testBuilder_MissingRequiredFields() {
        // Act & Assert
        assertThrows(IllegalStateException.class, () -> new RecursiveChainImpl.Builder().build());
        assertThrows(IllegalStateException.class, () -> new RecursiveChainImpl.Builder()
                .withOmmConsumer(ommConsumer)
                .build());
        assertThrows(IllegalStateException.class, () -> new RecursiveChainImpl.Builder()
                .withOmmConsumer(ommConsumer)
                .withChainName(CHAIN_NAME)
                .build());
    }
}
