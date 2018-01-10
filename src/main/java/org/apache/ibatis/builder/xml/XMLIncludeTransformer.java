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
package org.apache.ibatis.builder.xml;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.builder.IncompleteElementException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.parsing.PropertyParser;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.session.Configuration;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @author Frank D. Martinez [mnesarco]
 */
public class XMLIncludeTransformer {

  private final Configuration configuration;
  private final MapperBuilderAssistant builderAssistant;

  public XMLIncludeTransformer(Configuration configuration, MapperBuilderAssistant builderAssistant) {
    this.configuration = configuration;
    this.builderAssistant = builderAssistant;
  }

  /**
   * 解析<include>标签，进行sql拼接（只是部分sql的拼接，其结果还不是最终要执行的sql）
   * @param source
   */
  public void applyIncludes(Node source) {
    Properties variablesContext = new Properties();
    //获取Properties文件中的相关配置信息，这个是在解析主配置文件的时候设置到configuration对象中的
    Properties configurationVariables = configuration.getVariables();
    if (configurationVariables != null) {
      variablesContext.putAll(configurationVariables);
    }
    applyIncludes(source, variablesContext, false);
  }

  /**
   * Recursively apply includes through all SQL fragments.
   * @param source Include node in DOM tree
   * @param variablesContext Current context for static variables with values
   *
   * 从上边的注释中可以知道，这个方法会被递归调用，用来解析<select><update><delete><insert>中的<include>的过程，即拼接sql的过程
   * 只是部分sql的拼接，其结果还不是最终要执行的sql
   */
  private void applyIncludes(Node source, final Properties variablesContext, boolean included) {
    // 解析<include>标签，其解析可参考以下这段配置：
    /*
        <sql id="sometable">
          <!--  这是一个TEXT_NODE  -->
          ${prefix}Table
        </sql>

        <sql id="someinclude">
          from
            <include refid="${include_target}"/>
        </sql>

        <select id="select" resultType="map">
          select
            field1, field2, field3
          <include refid="someinclude">
            <property name="prefix" value="Some"/>
            <property name="include_target" value="sometable"/>
          </include>
        </select>
     */
    if (source.getNodeName().equals("include")) {
      // 先获取<include>标签的refid属性的值（可能是一个对Properties文件中变量的引用，如果是的话会先解析获取变量对应的值）
      // 然后获取对应的<sql>标签对应的Node对象。这个是之前解析<sql>标签的时候保存到configuration中的。
      Node toInclude = findSqlFragment(getStringAttribute(source, "refid"), variablesContext);
      // 解析<include>标签中包含的<property>标签，并构建一个Properties对象。如果没有<property>标签的话，将会返回第二个参数对应的Properties对象
      Properties toIncludeContext = getVariablesContext(source, variablesContext);
      // 递归调用来解析通过refid引用的<sql>标签
      applyIncludes(toInclude, toIncludeContext, true);
      // 以下是sql的拼接过程，即替换<select><update><delete><insert><sql>等标签中的<include>的过程
      if (toInclude.getOwnerDocument() != source.getOwnerDocument()) {
        toInclude = source.getOwnerDocument().importNode(toInclude, true);
      }
      source.getParentNode().replaceChild(toInclude, source);
      while (toInclude.hasChildNodes()) {
        toInclude.getParentNode().insertBefore(toInclude.getFirstChild(), toInclude);
      }
      toInclude.getParentNode().removeChild(toInclude);
    } else if (source.getNodeType() == Node.ELEMENT_NODE) {
      // 这里已知的可以解析的元素有 <sql> (其他的还没有看到，TODO 看到后随时填充)
      // 先获取其对应的子标签，然后递归调用依次解析每一个子标签
      NodeList children = source.getChildNodes();
      for (int i = 0; i < children.getLength(); i++) {
        applyIncludes(children.item(i), variablesContext, included);
      }
    } else if (included && source.getNodeType() == Node.TEXT_NODE
        && !variablesContext.isEmpty()) {
      // 解析TEXT_NODE（开始标签和关闭标签之间的文本），主要是用来将TEXT_NODE中对Properties文件或<property>标签定义的属性的引用进行替换
      // replace variables ins all text nodes
      source.setNodeValue(PropertyParser.parse(source.getNodeValue(), variablesContext));
    }
  }

  /**
   * 用来解析<include>标签中refid所引用的<sql>标签对应的XNode对象
   * @param refid
   * @param variables
   * @return
   */
  private Node findSqlFragment(String refid, Properties variables) {
    // 对Properties文件中变量引用的解析
    refid = PropertyParser.parse(refid, variables);
    refid = builderAssistant.applyCurrentNamespace(refid, true);
    try {
      XNode nodeToInclude = configuration.getSqlFragments().get(refid);
      return nodeToInclude.getNode().cloneNode(true);
    } catch (IllegalArgumentException e) {
      throw new IncompleteElementException("Could not find SQL statement to include with refid '" + refid + "'", e);
    }
  }

  private String getStringAttribute(Node node, String name) {
    return node.getAttributes().getNamedItem(name).getNodeValue();
  }

  /**
   * Read placeholders and their values from include node definition.
   * @param node Include node instance
   * @param inheritedVariablesContext Current context used for replace variables in new variables values
   * @return variables context from include instance (no inherited values)
   *
   * 用来解析<include>的子标签，mybatis-3-mapper.dtd中对<include>标签的描述：
   *      <!ELEMENT include (property+)?>
   *      <!ATTLIST include
   *        refid CDATA #REQUIRED
   *      >
   *
   * 对<property>标签的描述：
   *      <!ELEMENT property EMPTY>
   *      <!ATTLIST property
   *        name CDATA #REQUIRED
   *        value CDATA #REQUIRED
   *      >
   *
   *  由上可知，其只有<property>这一个属性，即跟准确的说这个方法是对一个特定的<include>标签的多个<property>的解析，并构建成Properties对象返回
   */
  private Properties getVariablesContext(Node node, Properties inheritedVariablesContext) {
    Map<String, String> declaredProperties = null;
    // 获取<include>的子标签，由dtd可知，<include>只允许有<property>子标签
    // 因此以下是对<property>的解析
    NodeList children = node.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node n = children.item(i);
      // 确保当前的子元素是element类型，即是一个标签，这里其实就是<property>标签
      if (n.getNodeType() == Node.ELEMENT_NODE) {
        // 获取<property>标签中的name属性的值
        String name = getStringAttribute(n, "name");
        // Replace variables inside
        // 获取<property>标签中value属性的值，如果是一个对Properties文件中变量的引用，这里会进行解析替换为实际值
        String value = PropertyParser.parse(getStringAttribute(n, "value"), inheritedVariablesContext);
        if (declaredProperties == null) {
          declaredProperties = new HashMap<String, String>();
        }
        // 确保在同一个<include>标签中的<property> 中name属性的唯一性，在put的时候对原始值进行了检查
        if (declaredProperties.put(name, value) != null) {
          throw new BuilderException("Variable " + name + " defined twice in the same include definition");
        }
      }
    }
    // 如果没有<property>标签的话，会把整个Properties文件中的相关配置的Properties对象返回，否则只返回多个<property>构成的Properties对象
    if (declaredProperties == null) {
      return inheritedVariablesContext;
    } else {
      Properties newProperties = new Properties();
      newProperties.putAll(inheritedVariablesContext);
      newProperties.putAll(declaredProperties);
      return newProperties;
    }
  }
}
