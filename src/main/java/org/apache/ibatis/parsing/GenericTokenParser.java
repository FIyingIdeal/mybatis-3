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
package org.apache.ibatis.parsing;

/**
 * @author Clinton Begin
 *
 * @commentauthor yanchao
 * @datetime 2018-1-10 14:29:58
 * @function 这个类主要负责对各类占位符（如${param}， #{param}等）进行解析，其解析过程委托给了{@link TokenHandler}的实现类
 */
public class GenericTokenParser {

  private final String openToken;
  private final String closeToken;
  private final TokenHandler handler;

  public GenericTokenParser(String openToken, String closeToken, TokenHandler handler) {
    this.openToken = openToken;
    this.closeToken = closeToken;
    this.handler = handler;
  }

  public String parse(String text) {
    if (text == null || text.isEmpty()) {
      return "";
    }
    char[] src = text.toCharArray();
    int offset = 0;
    // search open token
    int start = text.indexOf(openToken, offset);
    if (start == -1) {
      return text;
    }
    final StringBuilder builder = new StringBuilder();
    StringBuilder expression = null;
    // 开始对占位符（其实就是各类参数，如 #{param},${param}）的遍历
    while (start > -1) {
      // 判断openToken前边是否是'\\'，即是否被转义了，如果是的话这个openToken将会被视为sql的一部分，而不是占位符的开始
      // （这里不知道该如何进行更好的描述，其实openToken只是各类占位符的开始表示，其被解析的是整个占位符，而如果这个openToken被转义了的话，那它将不会被视为占位符的开始，而是当做sql的一部分）
      if (start > 0 && src[start - 1] == '\\') {
        // this open token is escaped. remove the backslash and continue.
        builder.append(src, offset, start - offset - 1).append(openToken);
        offset = start + openToken.length();
      } else {
        // found open token. let's search close token.
        // 先对expression进行一下解释，它其实就是占位符除去openToken和closeToken后的那个表达式，如某一个参数是如下所示：
        // #{name, javaType="String", jdbcType="String"}，那expression就是 name, javaType="String", jdbcType="String"
        if (expression == null) {
          expression = new StringBuilder();
        } else {
          // 当有多个参数的时候，在获取下一个expression之前需要将之前已被解析获得expression丢弃掉
          expression.setLength(0);
        }
        builder.append(src, offset, start - offset);
        offset = start + openToken.length();
        int end = text.indexOf(closeToken, offset);
        // 这个while是在已经找到了openToken的前提下寻找closeToken，使用while的意图是需要在找到已被转义了的closeToken后继续寻找真正的占位符结束标志
        while (end > -1) {
          if (end > offset && src[end - 1] == '\\') {
            // 如果找到了已被转义的closeToken，那它将会被视为expression的一部分，而不是作为占位符的结束标志
            // this close token is escaped. remove the backslash and continue.
            expression.append(src, offset, end - offset - 1).append(closeToken);
            offset = end + closeToken.length();
            end = text.indexOf(closeToken, offset);
          } else {
            // 拼接完整的表达式
            expression.append(src, offset, end - offset);
            offset = end + closeToken.length();
            break;
          }
        }
        if (end == -1) {
          // close token was not found.
          builder.append(src, start, src.length - start);
          offset = src.length;
        } else {
          // 使用handler（TokenHandler实现类的实例）对expression（占位符描述表达式，如 username,javaType=xxx,jdbcType=xxx）进行解析，不同的handler的handleToken处理逻辑不同
          // ParameterMappingTokenHandler#handleToken(String) 的处理逻辑是解析sql中的参数（#{param,javaType=xxx}）,并返回?来替换掉该参数（PreparedStatement就是使用?来做占位符的）
          builder.append(handler.handleToken(expression.toString()));
          offset = end + closeToken.length();
        }
      }
      start = text.indexOf(openToken, offset);
    }
    // 这里是用来拼接占位符后可能还存在的其他内容
    if (offset < src.length) {
      builder.append(src, offset, src.length - offset);
    }
    return builder.toString();
  }
}
