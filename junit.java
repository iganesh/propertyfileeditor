package com.refinitiv.platformservices.rt.objects.chain;

import com.refinitiv.ema.access.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ChainPackageTests {

    @Mock
    private OmmConsumer ommConsumer;

    @Mock
    private ReqMsg reqMsg;

    @Mock
    private RefreshMsg refreshMsg;

    @Mock
    private Map map;

    @Mock
    private ChainElementConsumer elementConsumer;

    // Mock for completion listener (avoiding OnCompletionListener import)
    @SuppressWarnings("unchecked")
    private Consumer<Chain> completionListener = mock(Consumer.class);

    @BeforeEach
    public void setUp() {
        when(ommConsumer.registerClient(any(ReqMsg.class), any(OmmConsumerClient.class), any()))
                .thenReturn(123L);
    }

    // FlatChain Tests
    @Test
    void testFlatChain_BuilderValidation_NullParameters() {
        assertThrows(RuntimeException.class, () -> FlatChain.Builder().build(),
                "Builder should throw exception for missing OmmConsumer");
        assertThrows(RuntimeException.class, () ->
                FlatChain.Builder().withChainName("0#TEST.CHA").build(),
                "Builder should throw exception for missing OmmConsumer");
        assertThrows(RuntimeException.class, () ->
                FlatChain.Builder().with(ommConsumer).build(),
                "Builder should throw exception for missing chain name");
        assertThrows(RuntimeException.class, () ->
                FlatChain.Builder().with(ommConsumer).withChainName("").build(),
                "Builder should throw exception for empty chain name");
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

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Synchronous subscription should complete");
        assertTrue(chain.isCompleted(), "Chain should be marked as completed");
        List<String> elements = chain.getElements();
        assertEquals(2, elements.size(), "Should retrieve 2 elements");
        assertTrue(elements.contains("TEST1"), "Elements should include TEST1");
        assertTrue(elements.contains("TEST2"), "Elements should include TEST2");
    }

    @Test
    void testFlatChain_AsynchronousSubscription_CompletionListener() throws InterruptedException {
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

        verify(completionListener, times(1)).accept(any(Chain.class));
        assertTrue(chain.isCompleted(), "Chain should be marked as completed");
        assertEquals(2, chain.getElements().size(), "Should retrieve 2 elements");
    }

    @Test
    void testFlatChain_SynchronousSubscription_EmptyChain() throws InterruptedException {
        FlatChain chain = FlatChain.Builder()
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

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Synchronous subscription should complete for empty chain");
        assertTrue(chain.isCompleted(), "Chain should be marked as completed");
        assertTrue(chain.getElements().isEmpty(), "Elements list should be empty");
    }

    @Test
    void testFlatChain_Subscription_Error() {
        FlatChain chain = FlatChain.Builder()
                .with(ommConsumer)
                .withChainName("0#TEST.CHA")
                .withServiceName("ELEKTRON_DD")
                .build();

        when(ommConsumer.registerClient(any(ReqMsg.class), any(OmmConsumerClient.class), any()))
                .thenThrow(new RuntimeException("Subscription failed"));

        CountDownLatch latch = new CountDownLatch(1);
        assertThrows(RuntimeException.class, () ->
                chain.subscribeSynchronously(() -> latch.countDown()), "Should throw exception on subscription failure");
        assertFalse(chain.isCompleted(), "Chain should not be marked as completed");
    }

    @Test
    void testFlatChain_Unsubscribe() {
        FlatChain chain = FlatChain.Builder()
                .with(ommConsumer)
                .withChainName("0#TEST.CHA")
                .withServiceName("ELEKTRON_DD")
                .build();

        chain.subscribeAsynchronously(); // Register handle
        chain.unsubscribe();
        verify(ommConsumer, times(1)).unregister(123L);
    }

    @Test
    void testFlatChain_GetNameAndService() {
        FlatChain chain = FlatChain.Builder()
                .with(ommConsumer)
                .withChainName("0#TEST.CHA")
                .withServiceName("ELEKTRON_DD")
                .build();

        assertEquals("0#TEST.CHA", chain.getName(), "Chain name should match");
        assertEquals("ELEKTRON_DD", chain.getServiceName(), "Service name should match");
    }

    // RecursiveChain Tests
    @Test
    void testRecursiveChain_BuilderValidation_NullParameters() {
        assertThrows(RuntimeException.class, () -> RecursiveChain.Builder().build(),
                "Builder should throw exception for missing OmmConsumer");
        assertThrows(RuntimeException.class, () ->
                RecursiveChain.Builder().withChainName("0#TEST.CHA").build(),
                "Builder should throw exception for missing OmmConsumer");
        assertThrows(RuntimeException.class, () ->
                RecursiveChain.Builder().with(ommConsumer).build(),
                "Builder should throw exception for missing chain name");
        assertThrows(RuntimeException.class, () ->
                RecursiveChain.Builder().with(ommConsumer).withChainName("").build(),
                "Builder should throw exception for empty chain name");
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

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Synchronous subscription should complete");
        assertTrue(chain.isCompleted(), "Chain should be marked as completed");
        List<String> elements = chain.getElements();
        assertEquals(3, elements.size(), "Should retrieve 3 elements");
        assertTrue(elements.contains("TEST1"), "Elements should include TEST1");
        assertTrue(elements.contains("TEST2"), "Elements should include TEST2");
        assertTrue(elements.contains("TEST3"), "Elements should include TEST3");
    }

    @Test
    void testRecursiveChain_AsynchronousSubscription_ChainElementConsumer() throws InterruptedException {
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
        assertTrue(chain.isCompleted(), "Chain should be marked as completed");
        assertEquals(2, chain.getElements().size(), "Should retrieve 2 elements");
    }

    @Test
    void testRecursiveChain_SynchronousSubscription_EmptyChain() throws InterruptedException {
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

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Synchronous subscription should complete for empty chain");
        assertTrue(chain.isCompleted(), "Chain should be marked as completed");
        assertTrue(chain.getElements().isEmpty(), "Elements list should be empty");
    }

    @Test
    void testRecursiveChain_Subscription_Error() {
        RecursiveChain chain = RecursiveChain.Builder()
                .with(ommConsumer)
                .withChainName("0#TEST.CHA")
                .withServiceName("ELEKTRON_DD")
                .build();

        when(ommConsumer.registerClient(any(ReqMsg.class), any(OmmConsumerClient.class), any()))
                .thenThrow(new RuntimeException("Subscription failed"));

        CountDownLatch latch = new CountDownLatch(1);
        assertThrows(RuntimeException.class, () ->
                chain.subscribeSynchronously(() -> latch.countDown()), "Should throw exception on subscription failure");
        assertFalse(chain.isCompleted(), "Chain should not be marked as completed");
    }

    @Test
    void testRecursiveChain_Unsubscribe() {
        RecursiveChain chain = RecursiveChain.Builder()
                .with(ommConsumer)
                .withChainName("0#TEST.CHA")
                .withServiceName("ELEKTRON_DD")
                .build();

        chain.subscribeAsynchronously(); // Register handle
        chain.unsubscribe();
        verify(ommConsumer, times(1)).unregister(123L);
    }

    @Test
    void testRecursiveChain_GetNameAndService() {
        RecursiveChain chain = RecursiveChain.Builder()
                .with(ommConsumer)
                .withChainName("0#TEST.CHA")
                .withServiceName("ELEKTRON_DD")
                .build();

        assertEquals("0#TEST.CHA", chain.getName(), "Chain name should match");
        assertEquals("ELEKTRON_DD", chain.getServiceName(), "Service name should match");
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
