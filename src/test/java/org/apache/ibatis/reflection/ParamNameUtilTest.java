package org.apache.ibatis.reflection;

import org.junit.Test;

import java.lang.reflect.Method;

public class ParamNameUtilTest {

    @Test
    public void testGetParamNames() throws Exception {

        Class clazz = ParamNameUtil.class;
        Method methods[] = clazz.getMethods();
        for(Method method : methods) {
            System.out.println(ParamNameUtil.getParamNames(method));
        }
    }
}
