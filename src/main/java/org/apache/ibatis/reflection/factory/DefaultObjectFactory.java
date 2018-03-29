/**
 *    Copyright 2009-2015 the original author or authors.
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
package org.apache.ibatis.reflection.factory;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.ibatis.reflection.ReflectionException;

/**
 * @author Clinton Begin
 *
 * @commentauthor yanchao
 * @datetime 2018-3-29 11:09:42
 * @function 为指定类型创建一个实例
 */
public class DefaultObjectFactory implements ObjectFactory, Serializable {

  private static final long serialVersionUID = -8855120656740914948L;

  /**
   * 创建指定类型实例的方法，调用了其重载的方法
   * 调用这个方法来创建实例的话其实是使用默认的无参构造方法来创建（即使无参构造方法是private的也可以）
   * 但有一点需要注意的是，如果type对应的类只提供了有参构造方法，而没有无参构造方法的话
   * 编译器是不会自动生成无参构造方法的，此时使用这个方法来创建实例的话会抛出异常
   */
  @Override
  public <T> T create(Class<T> type) {
    return create(type, null, null);
  }

  /**
   * 创建指定类型实例的方法，其使用的是构造方法来创建实例
   * @param type Object type  实例类型
   * @param constructorArgTypes Constructor argument types  构造方法的参数类型
   * @param constructorArgs Constructor argument values     构造方法的参数值
   */
  @SuppressWarnings("unchecked")
  @Override
  public <T> T create(Class<T> type, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
    // 处理interface类型，对部分interface类型指定了一个默认的实现类
    Class<?> classToCreate = resolveInterface(type);
    // we know types are assignable
    return (T) instantiateClass(classToCreate, constructorArgTypes, constructorArgs);
  }

  @Override
  public void setProperties(Properties properties) {
    // no props for default
  }

  /**
   * 使用构造方法实例化一个对象，具体是使用哪个构造方法，是由参数类型和参数值来确定的
   * @param type  实例类型
   * @param constructorArgTypes  构造方法的参数类型
   * @param constructorArgs      构造方法的参数值
   */
  <T> T instantiateClass(Class<T> type, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
    try {
      Constructor<T> constructor;
      // 构造函数参数类型或参数值有一个为空的话就使用默认构造方法（无参构造方法）来实例化对象
      if (constructorArgTypes == null || constructorArgs == null) {
        constructor = type.getDeclaredConstructor();
        if (!constructor.isAccessible()) {
          constructor.setAccessible(true);
        }
        return constructor.newInstance();
      }
      // 获取指定参数类型的构造方法
      constructor = type.getDeclaredConstructor(constructorArgTypes.toArray(new Class[constructorArgTypes.size()]));
      if (!constructor.isAccessible()) {
        constructor.setAccessible(true);
      }
      // 使用带参构造方法来实例化一个实例
      return constructor.newInstance(constructorArgs.toArray(new Object[constructorArgs.size()]));
    } catch (Exception e) {
      StringBuilder argTypes = new StringBuilder();
      if (constructorArgTypes != null && !constructorArgTypes.isEmpty()) {
        for (Class<?> argType : constructorArgTypes) {
          argTypes.append(argType.getSimpleName());
          argTypes.append(",");
        }
        argTypes.deleteCharAt(argTypes.length() - 1); // remove trailing ,
      }
      StringBuilder argValues = new StringBuilder();
      if (constructorArgs != null && !constructorArgs.isEmpty()) {
        for (Object argValue : constructorArgs) {
          argValues.append(String.valueOf(argValue));
          argValues.append(",");
        }
        argValues.deleteCharAt(argValues.length() - 1); // remove trailing ,
      }
      throw new ReflectionException("Error instantiating " + type + " with invalid types (" + argTypes + ") or values (" + argValues + "). Cause: " + e, e);
    }
  }

  /**
   * 处理type为接口的情况，这里只是对部分接口进行了处理，处理方法也很简单，为其指定了一个该接口的默认实现类
   * 如果是用户自定义的接口需要处理的话，可以继承这个类并重写这各方法
   */
  protected Class<?> resolveInterface(Class<?> type) {
    Class<?> classToCreate;
    if (type == List.class || type == Collection.class || type == Iterable.class) {
      classToCreate = ArrayList.class;
    } else if (type == Map.class) {
      classToCreate = HashMap.class;
    } else if (type == SortedSet.class) { // issue #510 Collections Support
      classToCreate = TreeSet.class;
    } else if (type == Set.class) {
      classToCreate = HashSet.class;
    } else {
      classToCreate = type;
    }
    return classToCreate;
  }

  /**
   * 判断指定的类是否是Collection类型
   */
  @Override
  public <T> boolean isCollection(Class<T> type) {
    return Collection.class.isAssignableFrom(type);
  }

}
