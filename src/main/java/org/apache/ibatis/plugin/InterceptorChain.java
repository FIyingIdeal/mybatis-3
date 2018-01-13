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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Clinton Begin
 *
 * @commentauthor yanchao
 * @datetime 2018-1-12 15:43:06
 * @reference http://zhangbo-peipei-163-com.iteye.com/blog/2033832?utm_source=tuicool&utm_medium=referral
 * 有关Mybatis的拦截器原理参考上边的链接内容，由浅入深讲解非常详细和易懂
 */
public class InterceptorChain {

  private final List<Interceptor> interceptors = new ArrayList<Interceptor>();

  /**
   * 获取代理对象，遍历所有的Interceptor定义，判断目标对象所属类是否被拦截，如果是的话就将其封装一下，所以最终返回的可能是一个经过多层代理的对象
   * @param target
   * @return
   */
  public Object pluginAll(Object target) {
    for (Interceptor interceptor : interceptors) {
      target = interceptor.plugin(target);
    }
    return target;
  }

  public void addInterceptor(Interceptor interceptor) {
    interceptors.add(interceptor);
  }
  
  public List<Interceptor> getInterceptors() {
    return Collections.unmodifiableList(interceptors);
  }

}
