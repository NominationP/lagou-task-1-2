package com.lagou.edu.factory;

import com.lagou.edu.annotation.MyAutowired;
import com.lagou.edu.annotation.MyService;
import com.lagou.edu.annotation.MyTransactional;
import com.lagou.edu.utils.TransactionManager;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * @author 应癫
 *
 *
 * 代理对象工厂：生成代理对象的
 */
@MyService()
public class ProxyFactory {

    @MyAutowired
    private TransactionManager transactionManager;

    private Object target;

    public void setTransactionManager(TransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    /**
     * 选择使用哪种方式代理方式
     * @return
     */
    public Object choiceProxy(Object object){
        this.target = object;
        /**
         * 可以直接使用cglibProxy动态代理
         */
        if(object.getClass().getInterfaces().length == 0){
            return getCglibProxy(object);
        }
        return getJdkProxy(object);
    }

    /**
     * Jdk动态代理
     * @param obj  委托对象
     * @return   代理对象
     */
    public Object getJdkProxy(Object obj) {
        // 获取代理对象
        return  Proxy.newProxyInstance(obj.getClass().getClassLoader(), obj.getClass().getInterfaces(),
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        Object result = null;
                        // 获取真实方法
                        Method realMethod = getRealMethod(method, args);
                        // 获取方法注解
                        MyTransactional annotation = realMethod.getAnnotation(MyTransactional.class);
                        transactionManager.closeTransaction();
                        // 有事务注解，则开启事务控制
                        if(annotation != null){
                            try{
                                // 开启事务(关闭事务的自动提交)
                                transactionManager.beginTransaction();
                                result = method.invoke(obj,args);
                                // 提交事务
                                transactionManager.commit();
                            }catch (Exception e) {
                                e.printStackTrace();
                                // 回滚事务
                                transactionManager.rollback();
                                // 抛出异常便于上层servlet捕获
                                throw e;
                            }
                        }
                        result = method.invoke(obj,args);
                        return result;
                    }
                });
    }


    /**
     * 使用cglib动态代理生成代理对象
     * @param obj 委托对象
     * @return
     */
    public Object getCglibProxy(Object obj) {
        return  Enhancer.create(obj.getClass(), new MethodInterceptor() {
            @Override
            public Object intercept(Object o, Method method, Object[] objects, MethodProxy methodProxy) throws Throwable {
                Object result = null;
                // 获取方法注解
                MyTransactional annotation = method.getAnnotation(MyTransactional.class);
                transactionManager.closeTransaction();
                if(annotation != null){
                    try{
                        // 开启事务(关闭事务的自动提交)
                        transactionManager.beginTransaction();
                        result = method.invoke(obj,objects);
                        // 提交事务
                        transactionManager.commit();
                    }catch (Exception e) {
                        e.printStackTrace();
                        // 回滚事务
                        transactionManager.rollback();
                        // 抛出异常便于上层servlet捕获
                        throw e;
                    }
                }
                result = method.invoke(obj,objects);
                return result;
            }
        });
    }

    /**
     * 获取动态代理类的真实方法
     * @param method
     * @param args
     * @return
     * @throws NoSuchMethodException
     */
    public Method getRealMethod(Method method, Object[] args) throws NoSuchMethodException {
        //获取真实方法
        Class<?> c = target.getClass();
        Method m;
        if(args == null){
            m = c.getMethod(method.getName());
        }else{
            Class[] methodArgs = new Class[args.length];
            for (int i = 0; i < methodArgs.length; i++) {
                methodArgs[i] = args[i].getClass();
            }
            m = c.getMethod(method.getName(),methodArgs);
        }
        return m;
    }
}
