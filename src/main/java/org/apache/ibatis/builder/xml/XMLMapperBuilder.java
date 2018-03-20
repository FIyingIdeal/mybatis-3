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
package org.apache.ibatis.builder.xml;

import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.builder.CacheRefResolver;
import org.apache.ibatis.builder.IncompleteElementException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.builder.ResultMapResolver;
import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Discriminator;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.ResultFlag;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

/**
 * @author Clinton Begin
 */
public class XMLMapperBuilder extends BaseBuilder {

  private XPathParser parser;
  private MapperBuilderAssistant builderAssistant;
  private Map<String, XNode> sqlFragments;
  private String resource;

  @Deprecated
  public XMLMapperBuilder(Reader reader, Configuration configuration, String resource, Map<String, XNode> sqlFragments, String namespace) {
    this(reader, configuration, resource, sqlFragments);
    this.builderAssistant.setCurrentNamespace(namespace);
  }

  @Deprecated
  public XMLMapperBuilder(Reader reader, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    this(new XPathParser(reader, true, configuration.getVariables(), new XMLMapperEntityResolver()),
        configuration, resource, sqlFragments);
  }

  public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource, Map<String, XNode> sqlFragments, String namespace) {
    this(inputStream, configuration, resource, sqlFragments);
    this.builderAssistant.setCurrentNamespace(namespace);
  }

  public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    this(new XPathParser(inputStream, true, configuration.getVariables(), new XMLMapperEntityResolver()),
        configuration, resource, sqlFragments);
  }

  private XMLMapperBuilder(XPathParser parser, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    super(configuration);
    this.builderAssistant = new MapperBuilderAssistant(configuration, resource);
    this.parser = parser;
    this.sqlFragments = sqlFragments;
    this.resource = resource;
  }

  /**
   * 这个方法做了以下几个事情：
   *  1. 判断当前的resource（是一个mapper文件的路径名）是否已被解析，如果未被解析则执行下边的步骤：
   *      1.1 对整个mapper配置文件进行解析；
   *      1.2 解析完成将这个resource添加到{@link Configuration#loadedResources}（已解析列表）当中；
   *      1.3 bindMapperForNamespace() 这个方法实现比较简单，但不知如何描述其作用...其中一步骤是将XML mapper注册到{@link Configuration#mapperRegistry}中
   *  2. 尝试重新解析解析失败的<resultMap/><cache-ref/>及sql语句相关标签（<select><insert><update><delete>）
   *     解析失败是因为多个mapper文件之间存在引用关系，而被引用的mapper文件还没有被解析
   */
  public void parse() {
    // 如果resource未解析，先进行解析
    if (!configuration.isResourceLoaded(resource)) {
      // 解析整个mapper配置文件
      configurationElement(parser.evalNode("/mapper"));
      // 将resource添加到{@link Configuration#loadedResources}中，它表示一个已解析列表
      configuration.addLoadedResource(resource);
      bindMapperForNamespace();
    }

    // 这里的几个parsePendingXXX()方法是用来重新解析之前解析失败的对应组件
    // 解析失败是因为多个mapper文件之间存在存在引用关系，而被引用的mapper文件还没有被解析
    parsePendingResultMaps();
    parsePendingChacheRefs();
    parsePendingStatements();
  }

  public XNode getSqlFragment(String refid) {
    return sqlFragments.get(refid);
  }

  private void configurationElement(XNode context) {
    try {
      // 获取<mapper>标签的namespace属性，该属性是必须的，如果没有指定的话将会抛出异常
      String namespace = context.getStringAttribute("namespace");
      if (namespace == null || namespace.equals("")) {
        throw new BuilderException("Mapper's namespace cannot be empty");
      }
      builderAssistant.setCurrentNamespace(namespace);
      cacheRefElement(context.evalNode("cache-ref"));
      cacheElement(context.evalNode("cache"));
      parameterMapElement(context.evalNodes("/mapper/parameterMap"));
      resultMapElements(context.evalNodes("/mapper/resultMap"));
      sqlElement(context.evalNodes("/mapper/sql"));
      // 解析sql配置
      buildStatementFromContext(context.evalNodes("select|insert|update|delete"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing Mapper XML. Cause: " + e, e);
    }
  }

  private void buildStatementFromContext(List<XNode> list) {
    if (configuration.getDatabaseId() != null) {
      buildStatementFromContext(list, configuration.getDatabaseId());
    }
    buildStatementFromContext(list, null);
  }

  private void buildStatementFromContext(List<XNode> list, String requiredDatabaseId) {
    for (XNode context : list) {
      final XMLStatementBuilder statementParser = new XMLStatementBuilder(configuration, builderAssistant, context, requiredDatabaseId);
      try {
        statementParser.parseStatementNode();
      } catch (IncompleteElementException e) {
        configuration.addIncompleteStatement(statementParser);
      }
    }
  }

  /**
   * 尝试重新解析之前解析失败的resultMap
   */
  private void parsePendingResultMaps() {
    Collection<ResultMapResolver> incompleteResultMaps = configuration.getIncompleteResultMaps();
    // TODO 为什么单单这里做了一个同步？
    synchronized (incompleteResultMaps) {
      Iterator<ResultMapResolver> iter = incompleteResultMaps.iterator();
      while (iter.hasNext()) {
        try {
          iter.next().resolve();
          iter.remove();
        } catch (IncompleteElementException e) {
          // ResultMap is still missing a resource...
        }
      }
    }
  }

  private void parsePendingChacheRefs() {
    Collection<CacheRefResolver> incompleteCacheRefs = configuration.getIncompleteCacheRefs();
    synchronized (incompleteCacheRefs) {
      Iterator<CacheRefResolver> iter = incompleteCacheRefs.iterator();
      while (iter.hasNext()) {
        try {
          iter.next().resolveCacheRef();
          iter.remove();
        } catch (IncompleteElementException e) {
          // Cache ref is still missing a resource...
        }
      }
    }
  }

  /**
   * 尝试重新解析之前解析失败的sql语句相关标签，如<select><insert><update><delete>
   */
  private void parsePendingStatements() {
    Collection<XMLStatementBuilder> incompleteStatements = configuration.getIncompleteStatements();
    synchronized (incompleteStatements) {
      Iterator<XMLStatementBuilder> iter = incompleteStatements.iterator();
      while (iter.hasNext()) {
        try {
          iter.next().parseStatementNode();
          iter.remove();
        } catch (IncompleteElementException e) {
          // Statement is still missing a resource...
        }
      }
    }
  }

  /**
   * 解析<cache-ref>标签，
   * @param context
   */
  private void cacheRefElement(XNode context) {
    if (context != null) {
      // 将cache-ref保存到configuration对象中，第一个参数表示当前namespace，第二个参数是引用的namespace
      configuration.addCacheRef(builderAssistant.getCurrentNamespace(), context.getStringAttribute("namespace"));
      CacheRefResolver cacheRefResolver = new CacheRefResolver(builderAssistant, context.getStringAttribute("namespace"));
      try {
        // 从configuration中获取指定namespace中的cache配置，并设置为当前namespace的cache配置
        cacheRefResolver.resolveCacheRef();
      } catch (IncompleteElementException e) {
        configuration.addIncompleteCacheRef(cacheRefResolver);
      }
    }
  }

  /**
   * 解析<cache>标签
   * @param context
   * @throws Exception
   */
  private void cacheElement(XNode context) throws Exception {
    if (context != null) {
      /**
       * 解析<cache>的type属性，它用来指定一个Cache的实现类，该Cache必须实现了{@link Cache}接口
       * 如果没有指定的话，默认取"PERPETUAL"，它是{@link org.apache.ibatis.cache.impl.PerpetualCache}的别名，是Mybatis提供的一个Cache实现
       */
      String type = context.getStringAttribute("type", "PERPETUAL");
      Class<? extends Cache> typeClass = typeAliasRegistry.resolveAlias(type);
      // 解析<cache>的eviction（收回策略）属性，默认是LRU，即最近最少使用
      // Mybatis提供的缓存收回策略包括：LRU(最近最少使用 LruCache.java)，FIFO(先进先出 FifoCache.java)，SOFT(软引用 SoftCache.java)，WEAK(弱引用 WeakCache.java)
      String eviction = context.getStringAttribute("eviction", "LRU");
      // 这些收回策略也都是一个实现了Cache接口的类，在此获取其对应的Class对象
      Class<? extends Cache> evictionClass = typeAliasRegistry.resolveAlias(eviction);
      // 解析<cache>的flushInterval（刷新间隔）属性，默认情况是不设置,也就是没有刷新间隔,缓存仅仅调用语句时刷新
      Long flushInterval = context.getLongAttribute("flushInterval");
      // 解析<cache>的size（引用数目）属性，默认值是1024。这个值是在LruCache.java中指定的，在通过CacheBuilder生成Cache实例的时候会设置一个装饰器Decorator，默认是LruCache
      Integer size = context.getIntAttribute("size");
      // 解析<cache>的readOnly（只读）属性，只读的缓存会给所有调用者返回缓存对象的相同实例。因此这些对象不能被修改。这提供了很重要的性能优势。
      // 可读写的缓存 会返回缓存对象的拷贝(通过序列化) 。这会慢一些,但是安全,因此默认是 false
      boolean readWrite = !context.getBooleanAttribute("readOnly", false);
      // 这个属性...官网没有提到...
      boolean blocking = context.getBooleanAttribute("blocking", false);
      Properties props = context.getChildrenAsProperties();
      // 构造Cache实例，并保存到configuration对象中，设置为当前Mapper的Cache
      builderAssistant.useNewCache(typeClass, evictionClass, flushInterval, size, readWrite, blocking, props);
    }
  }

  private void parameterMapElement(List<XNode> list) throws Exception {
    for (XNode parameterMapNode : list) {
      String id = parameterMapNode.getStringAttribute("id");
      String type = parameterMapNode.getStringAttribute("type");
      Class<?> parameterClass = resolveClass(type);
      List<XNode> parameterNodes = parameterMapNode.evalNodes("parameter");
      List<ParameterMapping> parameterMappings = new ArrayList<ParameterMapping>();
      for (XNode parameterNode : parameterNodes) {
        String property = parameterNode.getStringAttribute("property");
        String javaType = parameterNode.getStringAttribute("javaType");
        String jdbcType = parameterNode.getStringAttribute("jdbcType");
        String resultMap = parameterNode.getStringAttribute("resultMap");
        String mode = parameterNode.getStringAttribute("mode");
        String typeHandler = parameterNode.getStringAttribute("typeHandler");
        Integer numericScale = parameterNode.getIntAttribute("numericScale");
        ParameterMode modeEnum = resolveParameterMode(mode);
        Class<?> javaTypeClass = resolveClass(javaType);
        JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
        @SuppressWarnings("unchecked")
        Class<? extends TypeHandler<?>> typeHandlerClass = (Class<? extends TypeHandler<?>>) resolveClass(typeHandler);
        ParameterMapping parameterMapping = builderAssistant.buildParameterMapping(parameterClass, property, javaTypeClass, jdbcTypeEnum, resultMap, modeEnum, typeHandlerClass, numericScale);
        parameterMappings.add(parameterMapping);
      }
      builderAssistant.addParameterMap(id, parameterClass, parameterMappings);
    }
  }

  /**
   * 解析当前命名空间下的所有<resultMap>标签，通过for循环调用{@link XMLMapperBuilder#resultMapElement(XNode)}依次解析每一个<resultMap>构造ResultMap对象
   * @param list
   * @throws Exception
   */
  private void resultMapElements(List<XNode> list) throws Exception {
    for (XNode resultMapNode : list) {
      try {
        resultMapElement(resultMapNode);
      } catch (IncompleteElementException e) {
        // ignore, it will be retried
      }
    }
  }

  private ResultMap resultMapElement(XNode resultMapNode) throws Exception {
    return resultMapElement(resultMapNode, Collections.<ResultMapping> emptyList());
  }

  /**
   * 解析<resultMap>及其子元素，在解析<association>、<collection>、<case>标签的时候也会调用这个方法
   * 对于后三个标签重点是对type的解析，每一个标签对应的type属性的名字不相同，如 <association javaType="">、<collection ofType="" javaType="">、<case resultType="">
   * 在mybatis-3-mapper.dtd中对<resultMap>的描述：
   *    属性(ATTLIST)         ： id,type,extends,autoMapping
   *    元素/子标签(ELEMENT)  ： (constructor?,id*,result*,association*,collection*, discriminator?)
   * @param resultMapNode
   * @param additionalResultMappings
   * @return
   * @throws Exception
   */
  private ResultMap resultMapElement(XNode resultMapNode, List<ResultMapping> additionalResultMappings) throws Exception {
    ErrorContext.instance().activity("processing " + resultMapNode.getValueBasedIdentifier());

    ////解析<resultMap>标签的属性值开始////
    String id = resultMapNode.getStringAttribute("id",
        resultMapNode.getValueBasedIdentifier());
    // 由dtd描述可知，<resultMap>可拥有的属性(ATTLIST)只包括id,type,extends,autoMapping，且id与type是必须项
    // 但这里为什么会取ofType,resultType,javaType的属性值呢？
    // 因为这个方法不仅在解析<resultMap>时会被调用，在解析<association>、<collection>、<case>的时候也会被调用
    // type是<resultMap>中的属性
    String type = resultMapNode.getStringAttribute("type",
        // ofType是<collection>中的属性，在解析<collection>的时候这个方法可能会有返回值（这个属性是非必须的）
        resultMapNode.getStringAttribute("ofType",
            // resultType是<case>中的属性，在解析<case>的时候这个方法可能会有返回值（这个属性是非必须的）
            resultMapNode.getStringAttribute("resultType",
                // javaType是<collection>和<association>中共有的属性
                resultMapNode.getStringAttribute("javaType"))));
    String extend = resultMapNode.getStringAttribute("extends");
    Boolean autoMapping = resultMapNode.getBooleanAttribute("autoMapping");
    ////解析<resultMap>标签的属性值结束////

    Class<?> typeClass = resolveClass(type);
    Discriminator discriminator = null;
    List<ResultMapping> resultMappings = new ArrayList<ResultMapping>();
    resultMappings.addAll(additionalResultMappings);

    ////解析<resultMap>标签的子标签开始////
    List<XNode> resultChildren = resultMapNode.getChildren();
    // 在mybatis-3-mapper.dtd中对<resultMap>的描述中指定了其可拥有的元素/子标签(ELEMENT)包括(constructor?,id*,result*,association*,collection*, discriminator?)
    // 其中 ? 代表可以出现零或一次， * 代表可以出现零或多次，即<constructor>和<discriminator>只能出现一次，而其他标签可以出现多次
    // 从这个for循环可以知道，除了<constructor>和<discriminator>标签外，<resultMap>中其他的子标签都被解析成了一个ResultMapping对象
    for (XNode resultChild : resultChildren) {
      if ("constructor".equals(resultChild.getName())) {
        processConstructorElement(resultChild, typeClass, resultMappings);
      } else if ("discriminator".equals(resultChild.getName())) {
        discriminator = processDiscriminatorElement(resultChild, typeClass, resultMappings);
      } else {
        List<ResultFlag> flags = new ArrayList<ResultFlag>();
        if ("id".equals(resultChild.getName())) {
          flags.add(ResultFlag.ID);
        }
        resultMappings.add(buildResultMappingFromContext(resultChild, typeClass, flags));
      }
    }
    ////解析<resultMap>标签的子标签结束////

    ResultMapResolver resultMapResolver = new ResultMapResolver(builderAssistant, id, typeClass, extend, discriminator, resultMappings, autoMapping);
    try {
      return resultMapResolver.resolve();
    } catch (IncompleteElementException  e) {
      /**
       * IncompleteElementException这个异常对于ResultMap是在无法找到extend属性指定的父resultMap时抛出的
       * 当抛出这个异常的时候，会将当前这个resultMapResolver保存到 {@link Configuration#incompleteResultMaps} 当中
       * 当解析完整个mapper配置文件以后，会调用 {@link XMLMapperBuilder#parsePendingResultMaps()} 方法尝试再次解析
       */
      configuration.addIncompleteResultMap(resultMapResolver);
      throw e;
    }
  }

  /**
   * 解析<resultMap>子元素<constructor>
   * 在mybatis-3-mapper.dtd中对<constructor>的描述中指定了其可拥有的元素/子标签(ELEMENT)包括(idArg*,arg*)，不包含任何属性
   * @param resultChild <constructor>对应的XNode对象
   * @param resultType 对应<resultMap>中的type属性，即对应一个select查询的返回类型
   * @param resultMappings
   * @throws Exception
   */
  private void processConstructorElement(XNode resultChild, Class<?> resultType, List<ResultMapping> resultMappings) throws Exception {
    List<XNode> argChildren = resultChild.getChildren();
    for (XNode argChild : argChildren) {
      List<ResultFlag> flags = new ArrayList<ResultFlag>();
      flags.add(ResultFlag.CONSTRUCTOR);
      if ("idArg".equals(argChild.getName())) {
        flags.add(ResultFlag.ID);
      }
      resultMappings.add(buildResultMappingFromContext(argChild, resultType, flags));
    }
  }

  /**
   * 解析<resultMap>子元素<discriminator>
   * 在mybatis-3-mapper.dtd中对<discriminator>的描述
   *      元素/子标签(ELEMENT) ： (case+)
   *      属性(attr)          ： (column, javaType, jdbcType, typeHandler)
   * @param context
   * @param resultType
   * @param resultMappings
   * @return
   * @throws Exception
   */
  private Discriminator processDiscriminatorElement(XNode context, Class<?> resultType, List<ResultMapping> resultMappings) throws Exception {
    ////解析<discriminator>标签的属性值开始////
    String column = context.getStringAttribute("column");
    String javaType = context.getStringAttribute("javaType");
    String jdbcType = context.getStringAttribute("jdbcType");
    String typeHandler = context.getStringAttribute("typeHandler");
    Class<?> javaTypeClass = resolveClass(javaType);
    @SuppressWarnings("unchecked")
    Class<? extends TypeHandler<?>> typeHandlerClass = (Class<? extends TypeHandler<?>>) resolveClass(typeHandler);
    JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
    ////解析<discriminator>标签的属性值结束////

    ////解析<discriminator>的子元素<case>开始////
    // 在mybatis-3-mapper.dtd中对<case>的描述:
    //    元素/子标签(ELEMENT) ： (constructor?,id*,result*,association*,collection*, discriminator?)
    //    属性(attr)          ： value,resultMap,resultType
    Map<String, String> discriminatorMap = new HashMap<String, String>();
    for (XNode caseChild : context.getChildren()) {
      // 这里看似没有对resultType的解析，实际是在processNestedResultMappings(XNode, List)方法中进行了，
      // TODO 这里只对<case>标签的属性进行了解析，但还没有发现对其子标签解析的过程
      String value = caseChild.getStringAttribute("value");
      String resultMap = caseChild.getStringAttribute("resultMap", processNestedResultMappings(caseChild, resultMappings));
      discriminatorMap.put(value, resultMap);
    }
    ////解析<discriminator>的子元素<case>结束////

    // 构造Discriminator对象
    return builderAssistant.buildDiscriminator(resultType, column, javaTypeClass, jdbcTypeEnum, typeHandlerClass, discriminatorMap);
  }

  private void sqlElement(List<XNode> list) throws Exception {
    if (configuration.getDatabaseId() != null) {
      sqlElement(list, configuration.getDatabaseId());
    }
    sqlElement(list, null);
  }

  /**
   * 对<sql>标签的解析
   * mybatis-3-mapper.dtd中对<sql>的描述：
   *    <!ELEMENT sql (#PCDATA | include | trim | where | set | foreach | choose | if | bind)*>
   *    <!ATTLIST sql
   *       id CDATA #REQUIRED
   *       lang CDATA #IMPLIED
   *       databaseId CDATA #IMPLIED
   *    >
   * @param list
   * @param requiredDatabaseId
   * @throws Exception
   */
  private void sqlElement(List<XNode> list, String requiredDatabaseId) throws Exception {
    for (XNode context : list) {
      String databaseId = context.getStringAttribute("databaseId");
      String id = context.getStringAttribute("id");
      id = builderAssistant.applyCurrentNamespace(id, false);
      if (databaseIdMatchesCurrent(id, databaseId, requiredDatabaseId)) {
        sqlFragments.put(id, context);
      }
    }
  }
  
  private boolean databaseIdMatchesCurrent(String id, String databaseId, String requiredDatabaseId) {
    if (requiredDatabaseId != null) {
      if (!requiredDatabaseId.equals(databaseId)) {
        return false;
      }
    } else {
      if (databaseId != null) {
        return false;
      }
      // skip this fragment if there is a previous one with a not null databaseId
      if (this.sqlFragments.containsKey(id)) {
        XNode context = this.sqlFragments.get(id);
        if (context.getStringAttribute("databaseId") != null) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * 构建<resultMap>对应的ResultMapping对象
   * @param context
   * @param resultType
   * @param flags
   * @return
   * @throws Exception
   */
  private ResultMapping buildResultMappingFromContext(XNode context, Class<?> resultType, List<ResultFlag> flags) throws Exception {
    String property;
    if (flags.contains(ResultFlag.CONSTRUCTOR)) {
      property = context.getStringAttribute("name");
    } else {
      property = context.getStringAttribute("property");
    }
    String column = context.getStringAttribute("column");
    String javaType = context.getStringAttribute("javaType");
    String jdbcType = context.getStringAttribute("jdbcType");
    String nestedSelect = context.getStringAttribute("select");
    String nestedResultMap = context.getStringAttribute("resultMap",
        processNestedResultMappings(context, Collections.<ResultMapping> emptyList()));
    String notNullColumn = context.getStringAttribute("notNullColumn");
    String columnPrefix = context.getStringAttribute("columnPrefix");
    String typeHandler = context.getStringAttribute("typeHandler");
    String resultSet = context.getStringAttribute("resultSet");
    String foreignColumn = context.getStringAttribute("foreignColumn");
    boolean lazy = "lazy".equals(context.getStringAttribute("fetchType", configuration.isLazyLoadingEnabled() ? "lazy" : "eager"));
    Class<?> javaTypeClass = resolveClass(javaType);
    @SuppressWarnings("unchecked")
    Class<? extends TypeHandler<?>> typeHandlerClass = (Class<? extends TypeHandler<?>>) resolveClass(typeHandler);
    JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
    return builderAssistant.buildResultMapping(resultType, property, column, javaTypeClass, jdbcTypeEnum, nestedSelect, nestedResultMap, notNullColumn, columnPrefix, typeHandlerClass, flags, resultSet, foreignColumn, lazy);
  }

  /**
   * 这个方法主要用来解析<resultMap>的嵌套子标签，从该方法的两次调用中可知，该方法的调用时机是当子标签中对应的resultMap属性不存在的时候
   * 通过调用{@link XMLMapperBuilder#resultMapElement(XNode, List)}方法解析其对应的标签，最重要的是对type相关属性的解析
   * 如 <collection>  ->  ofType/javaType
   *    <association> ->  javaType
   *    <case>        ->  resultType
   * @param context
   * @param resultMappings
   * @return
   * @throws Exception
   */
  private String processNestedResultMappings(XNode context, List<ResultMapping> resultMappings) throws Exception {
    if ("association".equals(context.getName())
        || "collection".equals(context.getName())
        || "case".equals(context.getName())) {
      if (context.getStringAttribute("select") == null) {
        //解析<association>、<collection>或<case>标签中type相关的属性（resultMap、resultType、ofType、javaType）
        ResultMap resultMap = resultMapElement(context, resultMappings);
        return resultMap.getId();
      }
    }
    return null;
  }

  private void bindMapperForNamespace() {
    // 获取当前mapper配置文件的namespace，该值是interface的全限定名
    String namespace = builderAssistant.getCurrentNamespace();
    if (namespace != null) {
      Class<?> boundType = null;
      try {
        boundType = Resources.classForName(namespace);
      } catch (ClassNotFoundException e) {
        //ignore, bound type is not required
      }
      if (boundType != null) {
        if (!configuration.hasMapper(boundType)) {
          // Spring may not know the real resource name so we set a flag
          // to prevent loading again this resource from the mapper interface
          // look at MapperAnnotationBuilder#loadXmlResource
          // 将xml配置文件在 Configuration#loadResources 中注册了两次，一次是以xml文件的具体路径注册，一次是以 "namespace:" + ${namespace}
          // TODO 对namespace的注册看原注释是为了和spring整合使用的，还没看到
          configuration.addLoadedResource("namespace:" + namespace);
          configuration.addMapper(boundType);
        }
      }
    }
  }

}
