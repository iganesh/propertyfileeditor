package com.refinitiv.platformservices.rt.objects;

import com.refinitiv.ema.access.*;
import com.refinitiv.platformservices.rt.objects.chain.*;
import com.refinitiv.platformservices.rt.objects.common.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ValueAddObjectsTests {

    @Mock
    private OmmConsumer ommConsumer;

    @Mock
    private ReqMsg reqMsg;

    @Mock
    private OnCompletionListener completionListener;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(ommConsumer.registerClient(any(ReqMsg.class), any(OmmConsumerClient.class), any()))
                .thenReturn(123L); // Simulate successful registration
    }

    // Tests for common package
    @Test
    public void testSimpleLogger_LogMessage() {
        SimpleLogger logger = SimpleLogger.create();
        StringBuilder logOutput = new StringBuilder();
        
        // Redirect logger output to StringBuilder for testing
        SimpleLogger.setLogConsumer(message -> logOutput.append(message));
        
        String testMessage = "Test log message";
        logger.log(testMessage);
        
        assertTrue(logOutput.toString().contains(testMessage));
    }

    @Test
    public void testValueAddObjectsForEmaException() {
        String errorMessage = "Test exception";
        ValueAddObjectsForEmaException exception = new ValueAddObjectsForEmaException(errorMessage);
        
        assertEquals(errorMessage, exception.getMessage());
        assertNull(exception.getCause());
        
        Exception cause = new RuntimeException("Cause");
        exception = new ValueAddObjectsForEmaException(errorMessage, cause);
        
        assertEquals(errorMessage, exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    // Tests for chain package
    @Test
    public void testFlatChain_SubscribeSynchronously() throws InterruptedException {
        FlatChain chain = FlatChain.Builder()
                .with(ommConsumer)
                .withChainName("0#TEST.CHA")
                .withServiceName("ELEKTRON_DD")
                .build();
        
        // Mock the OmmConsumer behavior
        when(ommConsumer.registerClient(any(ReqMsg.class), any(OmmConsumerClient.class), any()))
                .thenAnswer(invocation -> {
                    OmmConsumerClient client = invocation.getArgument(1);
                    // Simulate receiving chain elements
                    client.onRefreshMsg(mockRefreshMsgWithChainElements(), ommConsumer);
                    return 123L;
                });

        CountDownLatch latch = new CountDownLatch(1);
        chain.subscribeSynchronously(() -> latch.countDown());
        
        boolean completed = latch.await(2, TimeUnit.SECONDS);
        assertTrue("Chain subscription should complete", completed);
        verify(ommConsumer, times(1)).registerClient(any(ReqMsg.class), any(OmmConsumerClient.class), any());
    }

    @Test
    public void testFlatChain_OnCompletionListener() throws InterruptedException {
        FlatChain chain = FlatChain.Builder()
                .with(ommConsumer)
                .withChainName("0#TEST.CHA")
                .withServiceName("ELEKTRON_DD")
                .withCompletionListener(completionListener)
                .build();
        
        when(ommConsumer.registerClient(any(ReqMsg.class), any(OmmConsumerClient.class), any()))
                .thenAnswer(invocation -> {
                    OmmConsumerClient client = invocation.getArgument(1);
                    client.onRefreshMsg(mockRefreshMsgWithChainElements(), ommConsumer);
                    return 123L;
                });

        chain.subscribeAsynchronously();
        
        // Wait for async processing
        Thread.sleep(1000);
        
        verify(completionListener, times(1)).onCompletion(any(Chain.class));
        assertTrue(chain.isCompleted());
    }

    @Test
    public void testRecursiveChain_FetchElements() throws InterruptedException {
        RecursiveChain chain = RecursiveChain.Builder()
                .with(ommConsumer)
                .withChainName("0#TEST.CHA")
                .withServiceName("ELEKTRON_DD")
                .build();
        
        when(ommConsumer.registerClient(any(ReqMsg.class), any(OmmConsumerClient.class), any()))
                .thenAnswer(invocation -> {
                    OmmConsumerClient client = invocation.getArgument(1);
                    client.onRefreshMsg(mockRefreshMsgWithChainElements(), ommConsumer);
                    return 123L;
                });

        CountDownLatch latch = new CountDownLatch(1);
        chain.subscribeSynchronously(() -> latch.countDown());
        
        boolean completed = latch.await(2, TimeUnit.SECONDS);
        assertTrue("Recursive chain subscription should complete", completed);
        
        List<String> elements = chain.getElements();
        assertFalse("Chain elements should not be empty", elements.isEmpty());
        assertTrue(elements.contains("TEST1"));
        assertTrue(elements.contains("TEST2"));
    }

    // Helper method to create a mock RefreshMsg with chain elements
    private RefreshMsg mockRefreshMsgWithChainElements() {
        RefreshMsg refreshMsg = mock(RefreshMsg.class);
        Map map = mock(Map.class);
        MapEntry mapEntry1 = mock(MapEntry.class);
        MapEntry mapEntry2 = mock(MapEntry.class);
        
        when(refreshMsg.payload()).thenReturn(mock(Data.class));
        when(refreshMsg.payload().dataType()).thenReturn(Data.DataType.DataTypes.MAP);
        when(refreshMsg.payload().map()).thenReturn(map);
        
        List<MapEntry> mapEntries = new ArrayList<>();
        mapEntries.add(mapEntry1);
        mapEntries.add(mapEntry2);
        
        when(map.iterator()).thenReturn(mapEntries.iterator());
        when(mapEntry1.name()).thenReturn("TEST1");
        when(mapEntry2.name()).thenReturn("TEST2");
        
        return refreshMsg;
    }
}
