package com.refinitiv.platformservices.rt.objects.chain;

import com.refinitiv.ema.access.OmmConsumer;
import com.refinitiv.platformservices.rt.objects.common.CompletionListener;
import com.refinitiv.platformservices.rt.objects.common.OmmConsumerSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ExtendWith(MockitoExtension.class)
class FlatChainImplTest {

    @Mock
    private OmmConsumer ommConsumer;

    @Mock
    private OmmConsumerSupplier ommConsumerSupplier;

    @InjectMocks
    private FlatChainImpl flatChain;

    @BeforeEach
    void setUp() {
        when(ommConsumerSupplier.get()).thenReturn(ommConsumer);
        flatChain = new FlatChainImpl.Builder()
                .withOmmConsumerSupplier(ommConsumerSupplier)
                .withServiceName("ELEKTRON_DD")
                .withName("0#TEST")
                .build();
    }

    @Test
    void testOpenAsync_Success() {
        try (MockedStatic<FlatChainImpl> mockedStatic = mockStatic(FlatChainImpl.class)) {
            CompletionListener listener = mock(CompletionListener.class);
            flatChain.openAsync(listener);

            verify(ommConsumerSupplier, times(1)).get();
            verify(listener, never()).onError(any());
            assertTrue(flatChain.isOpen());
        }
    }

    @Test
    void testGetElements_Success() {
        List<String> expectedElements = Arrays.asList("RIC1", "RIC2");
        flatChain.setElements(expectedElements); // Assuming a setter or internal method for testing

        List<String> elements = flatChain.getElements();
        assertEquals(expectedElements, elements);
    }

    @Test
    void testClose_Success() {
        flatChain.close();
        verify(ommConsumer, times(1)).unregister(anyLong());
        assertFalse(flatChain.isOpen());
    }
}
