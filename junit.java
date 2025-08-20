package com.refinitiv.platformservices.rt.objects;

import com.refinitiv.ema.access.*;
import com.refinitiv.platformservices.rt.objects.chain.*;
import com.refinitiv.platformservices.rt.objects.common.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ValueAddObjectsTests {

    @Mock
    private OmmConsumer ommConsumer;

    @Mock
    private ReqMsg reqMsg;

    @Mock
    private RefreshMsg refreshMsg;

    @Mock
    private Map map;

    @Mock
    private OmmConsumerClient ommConsumerClient;

    private SimpleLogger logger;
    private StringBuilder logOutput;

    @BeforeEach
    public void setUp() {
        logger = SimpleLogger.create();
        logOutput = new StringBuilder();
        SimpleLogger.setLogConsumer(message -> logOutput.append(message).append("\n"));
        when(ommConsumer.registerClient(any(ReqMsg.class), any(OmmConsumerClient.class), any()))
                .thenReturn(123L);
    }

    // Tests for common package
    @Test
    void testSimpleLogger_CreateAndLog() {
        SimpleLogger logger = SimpleLogger.create();
        logger.log("Test message");
        assertTrue(logOutput.toString().contains("Test message"));
    }

    @Test
    void testSimpleLogger_NullLogConsumer() {
        SimpleLogger.setLogConsumer(null);
        logger.log("Test message"); // Should not throw NPE
        assertFalse(logOutput.toString().contains("Test message"));
    }

    @Test
    void testSimpleLogger_MultipleLogs() {
        logger.log("First message");
        logger.log("Second message");
        assertTrue(logOutput.toString().contains("First message"));
        assertTrue(logOutput.toString().contains("Second message"));
    }

    @Test
    void testValueAddObjectsForEmaException_MessageOnly() {
        String message = "Test exception";
        ValueAddObjectsForEmaException ex = new ValueAddObjectsForEmaException(message);
        assertEquals(message, ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    void testValueAddObjectsForEmaException_MessageAndCause() {
        String message = "Test exception";
        Throwable cause = new RuntimeException("Root cause");
        ValueAddObjectsForEmaException ex = new ValueAddObjectsForEmaException(message, cause);
        assertEquals(message, ex.getMessage());
        assertEquals(cause, ex.getCause());
    }

    // Tests for chain package - FlatChain
    @Test
    void testFlatChain_BuilderValidation() {
        assertThrows(ValueAddObjectsForEmaException.class, () -> FlatChain.Builder().build());
        assertThrows(ValueAddObjectsForEmaException.class, () -> 
            FlatChain.Builder().withChainName("0#TEST.CHA").build());
        assertThrows(ValueAddObjectsForEmaException.class, () -> 
            FlatChain.Builder().with(ommConsumer).build());
    }

    @Test
    void testFlatChain_SynchronousSubscription_Success() throws InterruptedException {
        FlatChain chain = FlatChain.Builder()
                .with(ommConsumer)
                .withChainName("0#TEST.CHA")
                .withServiceName("ELEKTRON_DD")
                .build();

        when(ommConsumer.registerClient(any(ReqMsg.class), any(OmmConsumerClient.class), any()))
                .thenAnswer(invocation -> {
                    OmmConsumerClient client = invocation.getArgument(1);
                    client.onRefreshMsg(mockRefreshMsgWithElements("TEST1", "TEST2"), ommConsumer);
                    return 123L;
                });

        CountDownLatch latch = new CountDownLatch(1);
        chain.subscribeSynchronously(() -> latch.countDown());

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(chain.isCompleted());
        assertEquals(2, chain.getElements().size());
        assertTrue(chain.getElements().contains("TEST1"));
        assertTrue(chain.getElements().contains("TEST2"));
    }

    @Test
    void testFlatChain_AsynchronousSubscription_CompletionListener() throws InterruptedException {
        OnCompletionListener completionListener = mock(OnCompletionListener.class);
        FlatChain chain = FlatChain.Builder()
                .with(ommConsumer)
                .withChainName("0#TEST.CHA")
                .withServiceName("ELEKTRON_DD")
                .withCompletionListener(completionListener)
                .build();

        when(ommConsumer.registerClient(any(ReqMsg.class), any(OmmConsumerClient.class), any()))
                .thenAnswer(invocation -> {
                    OmmConsumerClient client = invocation.getArgument(1);
                    client.onRefreshMsg(mockRefreshMsgWithElements("TEST1", "TEST2"), ommConsumer);
                    return 123L;
                });

        chain.subscribeAsynchronously();
        Thread.sleep(1000);

        verify(completionListener, times(1)).onCompletion(chain);
        assertTrue(chain.isCompleted());
        assertEquals(2, chain.getElements().size());
    }

    @Test
    void testFlatChain_ErrorOnSubscription() throws InterruptedException {
        FlatChain chain = FlatChain.Builder()
                .with(ommConsumer)
                .withChainName("0#TEST.CHA")
                .withServiceName("ELEKTRON_DD")
                .build();

        when(ommConsumer.registerClient(any(ReqMsg.class), any(OmmConsumerClient.class), any()))
                .thenThrow(new RuntimeException("Subscription failed"));

        CountDownLatch latch = new CountDownLatch(1);
        assertThrows(ValueAddObjectsForEmaException.class, () -> 
            chain.subscribeSynchronously(() -> latch.countDown()));
        assertFalse(latch.await(1, TimeUnit.SECONDS));
    }

    @Test
    void testFlatChain_Unsubscribe() {
        FlatChain chain = FlatChain.Builder()
                .with(ommConsumer)
                .withChainName("0#TEST.CHA")
                .withServiceName("ELEKTRON_DD")
                .build();

        chain.unsubscribe();
        verify(ommConsumer, times(1)).unregister(123L);
    }

    // Tests for chain package - RecursiveChain
    @Test
    void testRecursiveChain_BuilderValidation() {
        assertThrows(ValueAddObjectsForEmaException.class, () -> RecursiveChain.Builder().build());
        assertThrows(ValueAddObjectsForEmaException.class, () -> 
            RecursiveChain.Builder().withChainName("0#TEST.CHA").build());
        assertThrows(ValueAddObjectsForEmaException.class, () -> 
            RecursiveChain.Builder().with(ommConsumer).build());
    }

    @Test
    void testRecursiveChain_SynchronousSubscription_MultiLevelChain() throws InterruptedException {
        RecursiveChain chain = RecursiveChain.Builder()
                .with(ommConsumer)
                .withChainName("0#TEST.CHA")
                .withServiceName("ELEKTRON_DD")
                .build();

        when(ommConsumer.registerClient(any(ReqMsg.class), any(OmmConsumerClient.class), any()))
                .thenAnswer(invocation -> {
                    OmmConsumerClient client = invocation.getArgument(1);
                    String chainName = invocation.getArgument(0, ReqMsg.class).name();
                    if (chainName.equals("0#TEST.CHA")) {
                        client.onRefreshMsg(mockRefreshMsgWithElements("1#SUB.CHA", "TEST1"), ommConsumer);
                    } else if (chainName.equals("1#SUB.CHA")) {
                        client.onRefreshMsg(mockRefreshMsgWithElements("TEST2", "TEST3"), ommConsumer);
                    }
                    return 123L;
                });

        CountDownLatch latch = new CountDownLatch(1);
        chain.subscribeSynchronously(() -> latch.countDown());

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(chain.isCompleted());
        List<String> elements = chain.getElements();
        assertEquals(2, elements.size());
        assertTrue(elements.contains("TEST1"));
        assertTrue(elements.contains("TEST2"));
    }

    @Test
    void testRecursiveChain_ChainElementConsumer() throws InterruptedException {
        ChainElementConsumer elementConsumer = mock(ChainElementConsumer.class);
        RecursiveChain chain = RecursiveChain.Builder()
                .with(ommConsumer)
                .withChainName("0#TEST.CHA")
                .withServiceName("ELEKTRON_DD")
                .withChainElementConsumer(elementConsumer)
                .build();

        when(ommConsumer.registerClient(any(ReqMsg.class), any(OmmConsumerClient.class), any()))
                .thenAnswer(invocation -> {
                    OmmConsumerClient client = invocation.getArgument(1);
                    client.onRefreshMsg(mockRefreshMsgWithElements("TEST1", "TEST2"), ommConsumer);
                    return 123L;
                });

        chain.subscribeAsynchronously();
        Thread.sleep(1000);

        verify(elementConsumer, times(2)).onChainElementReceived(anyString(), eq(chain));
        assertTrue(chain.isCompleted());
    }

    @Test
    void testRecursiveChain_EmptyChain() throws InterruptedException {
        RecursiveChain chain = RecursiveChain.Builder()
                .with(ommConsumer)
                .withChainName("0#TEST.CHA")
                .withServiceName("ELEKTRON_DD")
                .build();

        when(ommConsumer.registerClient(any(ReqMsg.class), any(OmmConsumerClient.class), any()))
                .thenAnswer(invocation -> {
                    OmmConsumerClient client = invocation.getArgument(1);
                    client.onRefreshMsg(mockRefreshMsgWithElements(), ommConsumer);
                    return 123L;
                });

        CountDownLatch latch = new CountDownLatch(1);
        chain.subscribeSynchronously(() -> latch.countDown());

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(chain.isCompleted());
        assertTrue(chain.getElements().isEmpty());
    }

    @Test
    void testChain_GetNameAndService() {
        FlatChain flatChain = FlatChain.Builder()
                .with(ommConsumer)
                .withChainName("0#TEST.CHA")
                .withServiceName("ELEKTRON_DD")
                .build();

        assertEquals("0#TEST.CHA", flatChain.getName());
        assertEquals("ELEKTRON_DD", flatChain.getServiceName());

        RecursiveChain recursiveChain = RecursiveChain.Builder()
                .with(ommConsumer)
                .withChainName("0#TEST.CHA")
                .withServiceName("ELEKTRON_DD")
                .build();

        assertEquals("0#TEST.CHA", recursiveChain.getName());
        assertEquals("ELEKTRON_DD", recursiveChain.getServiceName());
    }

    // Helper method to create a mock RefreshMsg with chain elements
    private RefreshMsg mockRefreshMsgWithElements(String... elements) {
        RefreshMsg refreshMsg = mock(RefreshMsg.class);
        Map map = mock(Map.class);
        when(refreshMsg.payload()).thenReturn(mock(Data.class));
        when(refreshMsg.payload().dataType()).thenReturn(Data.DataType.DataTypes.MAP);
        when(refreshMsg.payload().map()).thenReturn(map);

        List<MapEntry> mapEntries = new ArrayList<>();
        for (String element : elements) {
            MapEntry mapEntry = mock(MapEntry.class);
            when(mapEntry.name()).thenReturn(element);
            mapEntries.add(mapEntry);
        }

        when(map.iterator()).thenReturn(mapEntries.iterator());
        return refreshMsg;
    }
}
