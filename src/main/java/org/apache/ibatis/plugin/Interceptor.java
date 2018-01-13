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
package org.apache.ibatis.plugin;

import java.util.Properties;

/**
 * @author Clinton Begin
 *
 * @commentauthor yanchao
 * @datetime 2018-1-12 16:15:33
 * @reference http://zhangbo-peipei-163-com.iteye.com/blog/2033832?utm_source=tuicool&utm_medium=referral
 * 有关Mybatis的拦截器原理参考上边的链接内容，由浅入深讲解非常详细和易懂
 * @function 拦截器的抽象，实现类定义拦截器的具体执行内容
 */
public interface Interceptor {

  /**
   * 拦截器执行操作就是在这个方法内定义的，除此之外还需要在这里触发原始方法的执行：invocation.proceed();
   * @param invocation
   * @return
   * @throws Throwable
   */
  Object intercept(Invocation invocation) throws Throwable;

  /**
   * 迪米特法则：一个类对其他类知道的越少越好。
   * 这个方法就是为了让调用者不用去知道代理类（即Plugin）而添加的，这样调用者只需要知道Interceptor实现类就可以通过该方法获取代理对象
   * 该方法内部通过Plugin.warp()方法来获取代理对象
   */
  Object plugin(Object target);

  void setProperties(Properties properties);

}
