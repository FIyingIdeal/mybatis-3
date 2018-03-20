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
package org.apache.ibatis.builder;

import org.apache.ibatis.cache.Cache;

/**
 * @author Clinton Begin
 * @commentauthor yanchao
 * @datetime 2018-3-20 15:34:58
 * @function 处理mapper中<cache-ref>，获取指定namespace中的cache配置，并设置为当前namespace的cache配置
 */
public class CacheRefResolver {
  // 当前mapper的MapperBuilderAssistant对象，保存了当前mapper的相关配置，并辅助构造相关组件
  private final MapperBuilderAssistant assistant;
  // 被引用的Cache所在的namespace
  private final String cacheRefNamespace;

  public CacheRefResolver(MapperBuilderAssistant assistant, String cacheRefNamespace) {
    this.assistant = assistant;
    this.cacheRefNamespace = cacheRefNamespace;
  }

  // 从configuration中获取指定命名空间下的Cache实例，并设置为当前命名空间的Cache（即保存到当前命名空间下的MapperBuilderAssistant对象中）
  public Cache resolveCacheRef() {
    return assistant.useCacheRef(cacheRefNamespace);
  }
}