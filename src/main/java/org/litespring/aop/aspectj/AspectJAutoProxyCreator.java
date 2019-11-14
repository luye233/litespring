package org.litespring.aop.aspectj;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.litespring.aop.Advice;
import org.litespring.aop.MethodMatcher;
import org.litespring.aop.Pointcut;
import org.litespring.aop.framework.AopConfigSupport;
import org.litespring.aop.framework.AopProxyFactory;
import org.litespring.aop.framework.CglibProxyFactory;
import org.litespring.aop.framework.JdkAopProxyFactory;
import org.litespring.beans.BeansException;
import org.litespring.beans.factory.config.BeanPostProcessor;
import org.litespring.beans.factory.config.ConfigurableBeanFactory;
import org.litespring.util.ClassUtils;

public class AspectJAutoProxyCreator implements BeanPostProcessor {
	ConfigurableBeanFactory beanFactory;
	public Object beforeInitialization(Object bean, String beanName) throws BeansException {
		return bean;
	}

	/**
	 * 对bean进行增强，得到增强了的代理类对象
	 * @param bean
	 * @param beanName
	 * @return
	 * @throws BeansException
	 */
	public Object afterInitialization(Object bean, String beanName) throws BeansException {
		
		//如果这个Bean本身就是Advice及其子类，那就不要再生成动态代理了。
		if(isInfrastructureClass(bean.getClass())){
			return bean;
		}
		// 从factory遍历全部Advice，看有没有Advice要拦截该bean
		List<Advice> advices = getCandidateAdvices(bean);
		if(advices.isEmpty()){
			return bean;
		}
		
		return createProxy(advices,bean);
	}

	/**
	 * 筛选出会拦截目标bean的Advice
	 * @param bean
	 * @return
	 */
	private List<Advice> getCandidateAdvices(Object bean){
		
		List<Object> advices = this.beanFactory.getBeansByType(Advice.class);
		
		List<Advice> result = new ArrayList<Advice>();
		// 拿到全部Advice，一个个判断有没有要拦截，有的话，就把这个拦截器加入到结果集中
		for(Object o : advices){			
			Pointcut pc = ((Advice) o).getPointcut();
			if(canApply(pc,bean.getClass())){
				result.add((Advice) o);
			}
			
		}
		return result;
	}

	/**
	 * 创建代理对象
	 * 这里的代码跟前面的测试代码相似，没有好好看
	 * @param advices 拦截器列表
	 * @param bean 要执行的业务类（目标类）
	 * @return
	 */
	protected Object createProxy( List<Advice> advices ,Object bean) {

		AopConfigSupport config = new AopConfigSupport();
		for(Advice advice : advices){
			config.addAdvice(advice);
		}
		
		Set<Class> targetInterfaces = ClassUtils.getAllInterfacesForClassAsSet(bean.getClass());
		for (Class<?> targetInterface : targetInterfaces) {
			config.addInterface(targetInterface);
		}
		
		config.setTargetObject(bean);		
		
		AopProxyFactory proxyFactory = null;
		// 如果该类没有实现任何接口，使用cglib创建动态代理
		if(config.getProxiedInterfaces().length == 0){
			proxyFactory =  new CglibProxyFactory(config);
		} else{
			
			proxyFactory = new JdkAopProxyFactory(config);
		}	
	
		
		return proxyFactory.getProxy();
		
		
	}
	
	protected boolean isInfrastructureClass(Class<?> beanClass) {
		boolean retVal = Advice.class.isAssignableFrom(beanClass);
		
		return retVal;
	}
	
	public void setBeanFactory(ConfigurableBeanFactory beanFactory) {
		this.beanFactory = beanFactory;
		
	}

	/**
	 * 使用pointcut中的接口来判断某个类有没有方法要拦截
	 * @param pc
	 * @param targetClass
	 * @return
	 */
	public static boolean canApply(Pointcut pc, Class<?> targetClass) {
		MethodMatcher methodMatcher = pc.getMethodMatcher();	

		Set<Class> classes = new LinkedHashSet<Class>(ClassUtils.getAllInterfacesForClassAsSet(targetClass));
		classes.add(targetClass);
		for (Class<?> clazz : classes) {
			Method[] methods = clazz.getDeclaredMethods();			
			for (Method method : methods) {
				if (methodMatcher.matches(method/*, targetClass*/)) {
					return true;
				}
			}
		}

		return false;
	}

}
