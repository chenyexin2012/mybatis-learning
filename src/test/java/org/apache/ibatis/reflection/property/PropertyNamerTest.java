package org.apache.ibatis.reflection.property;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PropertyNamerTest {

    @Test
    public void testMethodToProperty() {

        assertEquals(PropertyNamer.methodToProperty("getUserCode"), "userCode");
        assertEquals(PropertyNamer.methodToProperty("setUserCode"), "userCode");
        assertEquals(PropertyNamer.methodToProperty("isTrue"), "true");
        assertEquals(PropertyNamer.methodToProperty("get"), "");
        assertEquals(PropertyNamer.methodToProperty("is"), "");
    }
}
