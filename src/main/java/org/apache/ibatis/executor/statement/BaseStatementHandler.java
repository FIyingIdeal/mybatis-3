/**
 *    Copyright 2009-2016 the original author or authors.
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
package org.apache.ibatis.executor.statement;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.resultset.ResultSetHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * @author Clinton Begin
 *
 * @commentauthor yanchao
 * @datetime 2018-3-30 15:10:53
 */
public abstract class BaseStatementHandler implements StatementHandler {

  protected final Configuration configuration;
  protected final ObjectFactory objectFactory;
  protected final TypeHandlerRegistry typeHandlerRegistry;
  protected final ResultSetHandler resultSetHandler;
  protected final ParameterHandler parameterHandler;

  protected final Executor executor;
  protected final MappedStatement mappedStatement;
  protected final RowBounds rowBounds;

  protected BoundSql boundSql;

  protected BaseStatementHandler(Executor executor, MappedStatement mappedStatement, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
    this.configuration = mappedStatement.getConfiguration();
    this.executor = executor;
    this.mappedStatement = mappedStatement;
    this.rowBounds = rowBounds;

    this.typeHandlerRegistry = configuration.getTypeHandlerRegistry();
    this.objectFactory = configuration.getObjectFactory();

    /**
     * 这个boundSql是否为空会因执行的操作不同而不同：
     *    如果是select操作的话，在{@link org.apache.ibatis.executor.BaseExecutor#query(MappedStatement, Object, RowBounds, ResultHandler)}
     *        中就先获取了BoundSql，然后传递到了{@link org.apache.ibatis.executor.SimpleExecutor#doQuery(MappedStatement, Object, RowBounds, ResultHandler, BoundSql)} 中
     *    如果是非select操作的话，在{@link org.apache.ibatis.executor.BaseExecutor#update(MappedStatement, Object)}
     *        方法中并没有获取BoundSql，且在{@link org.apache.ibatis.executor.SimpleExecutor#doUpdate(MappedStatement, Object)}
     *        中构造StatementHandler的时候，传递的BoundSql参数就是null，在BaseStatementHandler构造方法中的BoundSql就是一个null，会尝试执行获取主键的操作
     */
    if (boundSql == null) { // issue #435, get the key before calculating the statement
      // 生成主键，插入数据对象parameterObject当中
      generateKeys(parameterObject);
      boundSql = mappedStatement.getBoundSql(parameterObject);
    }

    this.boundSql = boundSql;

    this.parameterHandler = configuration.newParameterHandler(mappedStatement, parameterObject, boundSql);
    this.resultSetHandler = configuration.newResultSetHandler(executor, mappedStatement, rowBounds, parameterHandler, resultHandler, boundSql);
  }

  @Override
  public BoundSql getBoundSql() {
    return boundSql;
  }

  @Override
  public ParameterHandler getParameterHandler() {
    return parameterHandler;
  }

  @Override
  public Statement prepare(Connection connection, Integer transactionTimeout) throws SQLException {
    ErrorContext.instance().sql(boundSql.getSql());
    Statement statement = null;
    try {
      // 这里会调用Connection#prepareStatement方法来生成Statement对象，并且会根据KeyGenerator类型决定是否需要返回主键
      statement = instantiateStatement(connection);
      setStatementTimeout(statement, transactionTimeout);
      setFetchSize(statement);
      return statement;
    } catch (SQLException e) {
      closeStatement(statement);
      throw e;
    } catch (Exception e) {
      closeStatement(statement);
      throw new ExecutorException("Error preparing statement.  Cause: " + e, e);
    }
  }

  protected abstract Statement instantiateStatement(Connection connection) throws SQLException;

  protected void setStatementTimeout(Statement stmt, Integer transactionTimeout) throws SQLException {
    Integer queryTimeout = null;
    if (mappedStatement.getTimeout() != null) {
      queryTimeout = mappedStatement.getTimeout();
    } else if (configuration.getDefaultStatementTimeout() != null) {
      queryTimeout = configuration.getDefaultStatementTimeout();
    }
    if (queryTimeout != null) {
      stmt.setQueryTimeout(queryTimeout);
    }
    StatementUtil.applyTransactionTimeout(stmt, queryTimeout, transactionTimeout);
  }

  protected void setFetchSize(Statement stmt) throws SQLException {
    Integer fetchSize = mappedStatement.getFetchSize();
    if (fetchSize != null) {
      stmt.setFetchSize(fetchSize);
      return;
    }
    Integer defaultFetchSize = configuration.getDefaultFetchSize();
    if (defaultFetchSize != null) {
      stmt.setFetchSize(defaultFetchSize);
    }
  }

  protected void closeStatement(Statement statement) {
    try {
      if (statement != null) {
        statement.close();
      }
    } catch (SQLException e) {
      //ignore
    }
  }

  /**
   * 用于获取主键值，并将主键值设置到要插入数据库中的参数对象中
   * @param parameter
   */
  protected void generateKeys(Object parameter) {
    // 获取KeyGenerator类型
    KeyGenerator keyGenerator = mappedStatement.getKeyGenerator();
    ErrorContext.instance().store();
    // 这里主要是针对通过<selectKey>来生成主键的情况（SelectKeyGenerator）
    // 对于数据库自动生成主键的情况（Jdbc3KeyGenerator），这个方法不做任何操作
    keyGenerator.processBefore(executor, mappedStatement, null, parameter);
    ErrorContext.instance().recall();
  }

}
