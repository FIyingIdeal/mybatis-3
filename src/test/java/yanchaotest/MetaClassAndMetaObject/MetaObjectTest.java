package yanchaotest.MetaClassAndMetaObject;

import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.reflection.factory.DefaultObjectFactory;
import org.junit.Test;
import yanchaotest.objectFactory.PrivateConstructorClass;

import java.util.Arrays;
import java.util.Map;

/**
 * @author yanchao
 * @date 2018/3/29 17:54
 */
public class MetaObjectTest {

    public static void main(String[] args) {
        // 使用MetaObject.forObject()需要提供很多参数，比较繁琐，Mybatis提供了SystemMetaObject.forObject()
        // MetaObject metaObject = MetaObject.forObject(...)
        PrivateConstructorClass pcc = PrivateConstructorClass.forObject();
        MetaObject metaObject = SystemMetaObject.forObject(pcc);
        System.out.println(metaObject.hasGetter("message"));  // true Reflector会为Field自动添加MethodInvoker类型的getter和setter方法
        System.out.println(metaObject.hasSetter("message"));  // true
        System.out.println(metaObject.getValue("message"));   // null
        metaObject.setValue("message", "MetaObjectTest");
        System.out.println(metaObject.getValue("message"));   // MetaObjectTest
        Arrays.stream(metaObject.getSetterNames()).forEach(s -> System.out.println(s));

        MetaClass metaClass = MetaClass.forClass(PrivateConstructorClass.class, new DefaultReflectorFactory());
        Arrays.stream(metaClass.getSetterNames()).forEach(s -> System.out.println(s));
    }

    @Test
    public void mapMetaObject() {
        Map<String, Object> map = new DefaultObjectFactory().create(Map.class);
        System.out.println(map.getClass());
        MetaObject mapMetaObject = SystemMetaObject.forObject(map);
        mapMetaObject.setValue("test", "test");
        System.out.println(map.get("test"));
    }
}
