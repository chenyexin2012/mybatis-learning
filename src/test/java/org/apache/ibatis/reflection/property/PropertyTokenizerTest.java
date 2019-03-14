package org.apache.ibatis.reflection.property;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PropertyTokenizerTest {

    @Test
    public void testPropertyTokenizer() {

        PropertyTokenizer propertyTokenizer = new PropertyTokenizer("user.name.age.sex");
        assertEquals(propertyTokenizer.getName(), "user");
        assertEquals(propertyTokenizer.getIndexedName(), "user");
        assertTrue(propertyTokenizer.hasNext());
        assertEquals(propertyTokenizer.getChildren(), "name.age.sex");
        assertEquals(propertyTokenizer.getIndex(), null);

        propertyTokenizer = propertyTokenizer.next();
        assertEquals(propertyTokenizer.getName(), "name");
        assertEquals(propertyTokenizer.getIndexedName(), "name");
        assertTrue(propertyTokenizer.hasNext());
        assertEquals(propertyTokenizer.getChildren(), "age.sex");
        assertEquals(propertyTokenizer.getIndex(), null);


        propertyTokenizer = new PropertyTokenizer("user[1]");
        assertEquals(propertyTokenizer.getName(), "user");
        assertEquals(propertyTokenizer.getIndex(), "1");
        assertEquals(propertyTokenizer.getIndexedName(), "user[1]");
        assertFalse(propertyTokenizer.hasNext());
        assertEquals(propertyTokenizer.getChildren(), null);
    }
}
