package org.opengroup.osdu.storage.provider.azure.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ThreadScopeContextTest {

    @InjectMocks
    ThreadScopeContext sut;
    Map<String, Object> beans = new HashMap<>();

    @Test
    void getBean_shouldReturnBeanObject_ifBeanIsPresentInContext() {
        ReflectionTestUtils.setField(sut, "beans", beans);
        Object beanObj = new Object();
        sut.setBean("beanName", beanObj);

        Object beanObjReturned = sut.getBean("beanName");

        assertEquals(beanObj, beanObjReturned);
    }

    @Test
    void removeShouldCallDestructionCallBack_afterRemovingBeanFromBeansList_ifBeanIsPresentInExecutionContext() {
        ReflectionTestUtils.setField(sut, "beans", beans);
        Object beanObj = new Object();
        sut.setBean("beanName", beanObj);
        Runnable destructionCallback = mock(Runnable.class);
        doNothing().when(destructionCallback).run();
        sut.registerDestructionCallback("beanName", destructionCallback);


        Object beanObjReturned = sut.remove("beanName");

        assertEquals(beanObj, beanObjReturned);
        verify(destructionCallback, times(1)).run();
        assertEquals(0, beans.size());
    }

    @Test
    void removeShouldDoNothing_ifBeanIsAbsentInExecutionContext() {
        ReflectionTestUtils.setField(sut, "beans", beans);

        Object beanObjReturned = sut.remove("beanName");

        assertEquals(null, beanObjReturned);
    }

    @Test
    void clearShouldCallDestructionCallBack_andClearAllBeans_ifDestructionCallBackIsPresent() {
        ReflectionTestUtils.setField(sut, "beans", beans);
        Object beanObj = new Object();
        sut.setBean("beanName", beanObj);
        Runnable destructionCallback = mock(Runnable.class);
        doNothing().when(destructionCallback).run();
        sut.registerDestructionCallback("beanName", destructionCallback);

        sut.clear();

        verify(destructionCallback, times(1)).run();
        assertEquals(0, beans.size());
    }

    @Test
    void clearShouldNotCallDestructionCallBack_andClearAllBeans_ifDestructionCallBackIsAbsent() {
        ReflectionTestUtils.setField(sut, "beans", beans);
        Object beanObj = new Object();
        sut.setBean("beanName", beanObj);

        sut.clear();

        assertEquals(0, beans.size());
    }
}
