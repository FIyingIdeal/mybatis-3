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
package org.apache.ibatis.scripting.defaults;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeException;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 *
 * @commentauthor yanchao
 * @datetime 2018-4-9 13:56:46
 */
public class DefaultParameterHandler implements ParameterHandler {

  private final TypeHandlerRegistry typeHandlerRegistry;

  private final MappedStatement mappedStatement;
  /**
   * parameterObject是在{@link Configuration#newParameterHandler(MappedStatement, Object, BoundSql)}
   * 新建ParameterHandler的时候传入的查询参数的包装对象
   */
  private final Object parameterObject;
  private BoundSql boundSql;
  private Configuration configuration;

  public DefaultParameterHandler(MappedStatement mappedStatement, Object parameterObject, BoundSql boundSql) {
    this.mappedStatement = mappedStatement;
    this.configuration = mappedStatement.getConfiguration();
    this.typeHandlerRegistry = mappedStatement.getConfiguration().getTypeHandlerRegistry();
    this.parameterObject = parameterObject;
    this.boundSql = boundSql;
  }

  @Override
  public Object getParameterObject() {
    return parameterObject;
  }

  @Override
  public void setParameters(PreparedStatement ps) {
    ErrorContext.instance().activity("setting parameters").object(mappedStatement.getParameterMap().getId());
    List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
    if (parameterMappings != null) {
      for (int i = 0; i < parameterMappings.size(); i++) {
        ParameterMapping parameterMapping = parameterMappings.get(i);
        // 只过滤mode=in的ParameterMapping，默认在构造ParameterMapping的时候指定mode都为IN
        // 一般只在执行有返回值的存储过程的时候才会遇到设置mode=out的情况
        if (parameterMapping.getMode() != ParameterMode.OUT) {
          Object value;
          // 获取参数名
          String propertyName = parameterMapping.getProperty();
          // 获取参数值value
          if (boundSql.hasAdditionalParameter(propertyName)) { // issue #448 ask first for additional params
            /**
             * TODO （解决）additionalParameter是干什么的，可以通过什么方式赋值有待研究，但可以肯定的是其优先级最高
             * {@link org.apache.ibatis.scripting.xmltags.DynamicSqlSource#getBoundSql(Object)} 中
             * 为additionalParameter <=> {@link BoundSql#metaParameters} 设置值，但只有两个key：_parameter和_databaseId
             * 可以从这里获取到值。
             * 所以对于只有一个参数且没有使用@Param的查询，在sql中获取参数是在这个if中获取到值的
             */
            value = boundSql.getAdditionalParameter(propertyName);
          } else if (parameterObject == null) {
            // 如果参数为null的话直接将value置为null
            value = null;
          } else if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
            // 如果对应的参数设置了TypeHandler的话，直接将参数值赋值给value，在下边会使用TypeHandler处理值
            value = parameterObject;
          } else {
            MetaObject metaObject = configuration.newMetaObject(parameterObject);
            value = metaObject.getValue(propertyName);
          }
          TypeHandler typeHandler = parameterMapping.getTypeHandler();
          JdbcType jdbcType = parameterMapping.getJdbcType();
          if (value == null && jdbcType == null) {
            // 如果value和jdbcType都为null，设置jdbcType=JdbcType.OTHER
            jdbcType = configuration.getJdbcTypeForNull();
          }
          try {
            // 这里为PreparedStatement设置参数
            typeHandler.setParameter(ps, i + 1, value, jdbcType);
          } catch (TypeException e) {
            throw new TypeException("Could not set parameters for mapping: " + parameterMapping + ". Cause: " + e, e);
          } catch (SQLException e) {
            throw new TypeException("Could not set parameters for mapping: " + parameterMapping + ". Cause: " + e, e);
          }
        }
      }
    }
  }

}
