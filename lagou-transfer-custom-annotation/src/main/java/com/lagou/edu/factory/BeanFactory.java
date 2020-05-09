package com.lagou.edu.factory;

import com.alibaba.druid.util.StringUtils;
import com.lagou.edu.annotation.MyAutowired;
import com.lagou.edu.annotation.MyService;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.reflections.Reflections;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class BeanFactory {

    private static Map<String,Object> singletonObjects = new HashMap<>();

    /**
     * 加载beans.xml的静态方法
     */
    static {
        /**
         * 解析bean.xml
         */
        InputStream resourceAsStream = BeanFactory.class.getClassLoader().getResourceAsStream("beans.xml");
        SAXReader saxReader = new SAXReader();
        try {
            Document document = saxReader.read(resourceAsStream);
            Element rootElement = document.getRootElement();
            normalAnalysis(rootElement);
            List<Element> beanList = rootElement.selectNodes("//component-scan");
            for (Element element : beanList) {
                /**
                 * 获取包路径
                 */
                String aPackage = element.attributeValue("package");
                Reflections f = new Reflections(aPackage);
                /**
                 * 获取含有 MyService 注解的类
                 */
                Set<Class<?>> set = f.getTypesAnnotatedWith(MyService.class);
                /**
                 * 处理这些类：放入单例池，注入，代理
                 */
                classHandle(set);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void classHandle(Set<Class<?>> set) throws Exception{
        for (Class<?> aClass : set) {
            MyService annotation = aClass.getAnnotation(MyService.class);
            String beanKey = getBeaKey(aClass, annotation.value());
            /**
             * 将MyService标注的类放入单例池
             */
            singletonObjects.put(beanKey, aClass.newInstance());
        }

        /**
         * 属性注入
         */
        for (Class<?> aClass : set) {

            MyService annotation = aClass.getAnnotation(MyService.class);
            String beanKey = getBeaKey(aClass, annotation.value());
            Object parentObject = singletonObjects.get(beanKey);
            Field[] fields = aClass.getDeclaredFields();

            for (Field field : fields) {

                /**
                 * 判断属性是否需要注入
                 */
                field.setAccessible(true);
                MyAutowired hasMyAutowired = field.getAnnotation(MyAutowired.class);
                if(hasMyAutowired != null){

                    /**
                     * 通过 set+MethodName 方法注入属性
                     */
                    Method[] methods = aClass.getMethods();
                    /**
                     * 有的属性属于service，上一步加入了单例池，所以现在拿出来
                     */
                    Object diObject = singletonObjects.get(field.getName());
                    for (int j = 0; j < methods.length; j++) {
                        Method method = methods[j];
                        String setMonthod = getMethodNameUpperCase(field.getName());
                        if(method.getName().equalsIgnoreCase("set" + setMonthod)) {
                            method.invoke(parentObject, diObject);
                        }
                    }
                }
            }
            /**
             * 把单例池中的Service注解类都替换成动态代理类
             */
            if(aClass.getSimpleName().contains("Service")){
                ProxyFactory proxyFactory = (ProxyFactory) singletonObjects.get("proxyFactory");
                parentObject = proxyFactory.choiceProxy(parentObject);
            }
            singletonObjects.put(beanKey, parentObject);
        }
    }

    // 任务二：对外提供获取实例对象的接口（根据id获取）
    public static  Object getBean(String id) {
        return singletonObjects.get(id);
    }

    public static void normalAnalysis(Element rootElement) throws  Exception{
        List<Element> beanList = rootElement.selectNodes("//bean");
        for (int i = 0; i < beanList.size(); i++) {
            Element element =  beanList.get(i);
            // 处理每个bean元素，获取到该元素的id 和 class 属性
            String id = element.attributeValue("id");        // accountDao
            String clazz = element.attributeValue("class");  // com.lagou.edu.dao.impl.JdbcAccountDaoImpl
            // 通过反射技术实例化对象
            Class<?> aClass = Class.forName(clazz);
            Object o = aClass.newInstance();  // 实例化之后的对象
            // 存储到map中待用
            singletonObjects.put(id,o);
        }
        // 实例化完成之后维护对象的依赖关系，检查哪些对象需要传值进入，根据它的配置，我们传入相应的值
        // 有property子元素的bean就有传值需求
        List<Element> propertyList = rootElement.selectNodes("//property");
        // 解析property，获取父元素
        for (int i = 0; i < propertyList.size(); i++) {
            Element element =  propertyList.get(i);   //<property name="AccountDao" ref="accountDao"></property>
            String name = element.attributeValue("name");
            String ref = element.attributeValue("ref");
            // 找到当前需要被处理依赖关系的bean
            Element parent = element.getParent();
            // 调用父元素对象的反射功能
            String parentId = parent.attributeValue("id");
            Object parentObject = singletonObjects.get(parentId);
            // 遍历父对象中的所有方法，找到"set" + name
            Method[] methods = parentObject.getClass().getMethods();
            for (int j = 0; j < methods.length; j++) {
                Method method = methods[j];
                if(method.getName().equalsIgnoreCase("set" + name)) {  // 该方法就是 setAccountDao(AccountDao accountDao)
                    method.invoke(parentObject, singletonObjects.get(ref));
                }
            }
            // 把处理之后的parentObject重新放到map中
            singletonObjects.put(parentId,parentObject);
        }
    }

    public static String getBeaKey(Class<?> aClass, String value) throws Exception {

        if(StringUtils.isEmpty(value)){
            return getMethodNameTransLowerCase(aClass.getSimpleName());
        }
        return value;
    }

    private static String getMethodNameUpperCase(String fildeName) throws Exception{
        byte[] items = fildeName.getBytes();
        items[0] = (byte) ((char) items[0] - 'a' + 'A');
        return new String(items);
    }

    private static String getMethodNameTransLowerCase(String fildeName) throws Exception{
        byte[] items = fildeName.getBytes();
        items[0] = (byte) ((char) items[0] - 'A' + 'a');
        return new String(items);
    }

}
