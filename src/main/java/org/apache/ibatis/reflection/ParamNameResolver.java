/**
 *    Copyright 2009-2017 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package org.apache.ibatis.reflection;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

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
    final Class<?>[] paramTypes = method.getParameterTypes();
    // 获取方法参数上的注解，二维数组：第一维是参数的index；第二维是指定index上参数的注解（一个参数可以有多个注解）
    final Annotation[][] paramAnnotations = method.getParameterAnnotations();
    final SortedMap<Integer, String> map = new TreeMap<Integer, String>();
    // 参数个数
    int paramCount = paramAnnotations.length;
    // get names from @Param annotations
    for (int paramIndex = 0; paramIndex < paramCount; paramIndex++) {
      // 排除参数类型为 RowBounds 和 ResultHandler 的参数
      if (isSpecialParameter(paramTypes[paramIndex])) {
        // skip special parameters
        continue;
      }
      String name = null;
      // 遍历paramIndex（参数索引）对应的参数上的注解，筛选出@Param注解上的值
      for (Annotation annotation : paramAnnotations[paramIndex]) {
        if (annotation instanceof Param) {
          hasParamAnnotation = true;
          name = ((Param) annotation).value();
          break;
        }
      }
      if (name == null) {
        // @Param was not specified.
        // 没有找到@Param参数的话，尝试通过java8提供的Parameter类来获取参数
        if (config.isUseActualParamName()) {
          name = getActualParamName(method, paramIndex);
        }
        // 如果name依然为空（即不是使用的java8+），则给参数设置参数名称为0,1,2...该值取决于参数index及参数类型（非RowBound和ResultHandler）
        if (name == null) {
          // use the parameter index as the name ("0", "1", ...)
          // gcode issue #71
          name = String.valueOf(map.size());
        }
      }
      map.put(paramIndex, name);
    }
    names = Collections.unmodifiableSortedMap(map);
  }

  private String getActualParamName(Method method, int paramIndex) {
    if (Jdk.parameterExists) {
      // 获取指定index上参数的名称，如果编译的时候没有使用-parameters参数的话，则获取到的参数名称为arg0，arg1...之类的形式
      return ParamNameUtil.getParamNames(method).get(paramIndex);
    }
    return null;
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
   * A single non-special parameter is returned without a name.<br />
   * Multiple parameters are named using the naming rule.<br />
   * In addition to the default names, this method also adds the generic names (param1, param2,
   * ...).
   * </p>
   * 解析方法参数，分多种情况（一下提到的参数个数是所有方法参数除去RowBound和ResultHandler类型参数后剩余的参数个数）：
   *    1. 如果没有参数，则直接返回null；
   *    2. 如果只有一个参数且没有使用@Param标注，返回参数值；
   *    3. 如果只有一个参数且使用了@Param标注或方法有多个参数时，将参数封装成一个{@link ParamMap}，其本质是一个HashMap，key值为参数名称，value为参数值
   */
  public Object getNamedParams(Object[] args) {
    final int paramCount = names.size();
    if (args == null || paramCount == 0) {
      // 没有参数返回null
      return null;
    } else if (!hasParamAnnotation && paramCount == 1) {
      // 方法只有一个参数且没有使用@Param注解标注，直接获取args的第一个元素（names是Map<Integer, String>，key值为参数索引，value为参数名称）
      return args[names.firstKey()];
    } else {
      // 多参数情况处理
      final Map<String, Object> param = new ParamMap<Object>();
      int i = 0;
      // 将参数保存到一个HashMap当中，key为参数名，value为参数值
      for (Map.Entry<Integer, String> entry : names.entrySet()) {
        param.put(entry.getValue(), args[entry.getKey()]);
        // add generic param names (param1, param2, ...)
        final String genericParamName = GENERIC_NAME_PREFIX + String.valueOf(i + 1);
        // ensure not to overwrite parameter named with @Param
        if (!names.containsValue(genericParamName)) {
          // TODO 将参数以key值为param1,param2...形式又保存了一次
          param.put(genericParamName, args[entry.getKey()]);
        }
        i++;
      }
      return param;
    }
  }
}
