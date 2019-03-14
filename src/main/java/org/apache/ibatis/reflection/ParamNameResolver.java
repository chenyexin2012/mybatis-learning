/**
 * Copyright 2009-2018 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ibatis.reflection;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * 参数名称解析器，一般顺序：
 * 1.@Param注解
 * 2.参数名称 arg0, arg1...
 * 3.参数在方法中所有参数的位置索引 param1, param2...
 */
public class ParamNameResolver {

    private static final String GENERIC_NAME_PREFIX = "param";

    /**
     * <p>
     * The key is the index and the value is the name of the parameter.<br />
     * The name is obtained from {@link Param} if specified. When {@link Param} is not specified,
     * the parameter index is used. Note that this index could be different from the actual index
     * when the method has special parameters (i.e. {@link RowBounds} or {@link ResultHandler}).
     * </p>
     * <ul>
     * <li>aMethod(@Param("M") int a, @Param("N") int b) -&gt; {{0, "M"}, {1, "N"}}</li>
     * <li>aMethod(int a, int b) -&gt; {{0, "0"}, {1, "1"}}</li>
     * <li>aMethod(int a, RowBounds rb, int b) -&gt; {{0, "0"}, {2, "1"}}</li>
     * </ul>
     */
    private final SortedMap<Integer, String> names;

    private boolean hasParamAnnotation;

    public ParamNameResolver(Configuration config, Method method) {
        // 获取参数类型
        final Class<?>[] paramTypes = method.getParameterTypes();
        // 获取参数注解，每个参数可能对应多个
        final Annotation[][] paramAnnotations = method.getParameterAnnotations();
        // 使用TreeMap保证遍历有序
        final SortedMap<Integer, String> map = new TreeMap<>();
        // 参数数量
        int paramCount = paramAnnotations.length;
        // get names from @Param annotations
        for (int paramIndex = 0; paramIndex < paramCount; paramIndex++) {
            // RowBounds或ResultHandler以及它们的子类，跳过
            if (isSpecialParameter(paramTypes[paramIndex])) {
                // skip special parameters
                continue;
            }
            String name = null;
            // 遍历该参数的注解
            for (Annotation annotation : paramAnnotations[paramIndex]) {
                // 找到@Param注解
                if (annotation instanceof Param) {
                    hasParamAnnotation = true;
                    name = ((Param) annotation).value();
                    break;
                }
            }
            if (name == null) {
                // @Param was not specified.
                if (config.isUseActualParamName()) {
                    // setting配置中允许使用方法签名中的名称作为语句参数名称
                    // 则直接获取参数名称 arg0, arg1...
                    name = getActualParamName(method, paramIndex);
                }
                if (name == null) {
                    // use the parameter index as the name ("0", "1", ...)
                    // gcode issue #71
                    // 取参数在方法中的位置索引
                    name = String.valueOf(map.size());
                }
            }
            map.put(paramIndex, name);
        }
        names = Collections.unmodifiableSortedMap(map);
    }

    private String getActualParamName(Method method, int paramIndex) {
        return ParamNameUtil.getParamNames(method).get(paramIndex);
    }

    private static boolean isSpecialParameter(Class<?> clazz) {
        return RowBounds.class.isAssignableFrom(clazz) || ResultHandler.class.isAssignableFrom(clazz);
    }

    /**
     * Returns parameter names referenced by SQL providers.
     */
    public String[] getNames() {
        return names.values().toArray(new String[0]);
    }

    /**
     * <p>
     * A single non-special parameter is returned without a name.
     * Multiple parameters are named using the naming rule.
     * In addition to the default names, this method also adds the generic names (param1, param2,
     * ...).
     * </p>
     */
    public Object getNamedParams(Object[] args) {
        final int paramCount = names.size();
        if (args == null || paramCount == 0) {
            // 没有参数，直接返回null
            return null;
        } else if (!hasParamAnnotation && paramCount == 1) {
            // 没有@Param注解且只有一个参数，根据下标返回参数
            return args[names.firstKey()];
        } else {
            final Map<String, Object> param = new ParamMap<>();
            int i = 0;
            for (Map.Entry<Integer, String> entry : names.entrySet()) {
                // 参数名称和参数值
                param.put(entry.getValue(), args[entry.getKey()]);
                // add generic param names (param1, param2, ...)
                final String genericParamName = GENERIC_NAME_PREFIX + String.valueOf(i + 1);
                // ensure not to overwrite parameter named with @Param
                if (!names.containsValue(genericParamName)) {
                    // 添加通用参数名(param1, param2, ...)和参数值
                    param.put(genericParamName, args[entry.getKey()]);
                }
                i++;
            }
            return param;
        }
    }
}
