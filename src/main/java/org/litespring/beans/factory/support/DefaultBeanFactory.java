package org.litespring.beans.factory.support;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.litespring.beans.BeanDefinition;
import org.litespring.beans.BeansException;
import org.litespring.beans.PropertyValue;
import org.litespring.beans.SimpleTypeConverter;
import org.litespring.beans.factory.BeanCreationException;
import org.litespring.beans.factory.BeanFactoryAware;
import org.litespring.beans.factory.NoSuchBeanDefinitionException;
import org.litespring.beans.factory.config.BeanPostProcessor;
import org.litespring.beans.factory.config.DependencyDescriptor;
import org.litespring.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.litespring.util.ClassUtils;

public class DefaultBeanFactory  extends AbstractBeanFactory
	implements BeanDefinitionRegistry{
	private static final Log logger = LogFactory.getLog(DefaultBeanFactory.class);
	private List<BeanPostProcessor> beanPostProcessors = new ArrayList<BeanPostProcessor>();
	
	private final Map<String, BeanDefinition> beanDefinitionMap = new ConcurrentHashMap<String, BeanDefinition>(64);
	private ClassLoader beanClassLoader;
	
	public DefaultBeanFactory() {
		
	}
	public void addBeanPostProcessor(BeanPostProcessor postProcessor){
		this.beanPostProcessors.add(postProcessor);
	}
	public List<BeanPostProcessor> getBeanPostProcessors() {
		return this.beanPostProcessors;
	}
	public void registerBeanDefinition(String beanID,BeanDefinition bd){
		this.beanDefinitionMap.put(beanID, bd);
	}
	public BeanDefinition getBeanDefinition(String beanID) {
			
		return this.beanDefinitionMap.get(beanID);
	}

	/**
	 * 为从BeanFactory中拿到与type相关的Bean
	 * @param type
	 * @return
	 */
	public List<Object> getBeansByType(Class<?> type){
		List<Object> result = new ArrayList<Object>();
		// 拿到beanId
		List<String> beanIDs = this.getBeanIDsByType(type);
		for(String beanID : beanIDs){
			// get方法对Bean进行创建
			result.add(this.getBean(beanID));
		}
		return result;		
	}

	/**
	 * 遍历全部的BeanDefinition，如果其中的beanClass是type类型，就加到结果集中
	 * @param type
	 * @return
	 */
	private List<String> getBeanIDsByType(Class<?> type){
		List<String> result = new ArrayList<String>();
		for(String beanName :this.beanDefinitionMap.keySet()){
			Class<?> beanClass = null;
			try{
				beanClass = this.getType(beanName);
			}catch(Exception e){
				logger.warn("can't load class for bean :"+beanName+", skip it.");
				continue;
			}
			
			if((beanClass != null) && type.isAssignableFrom(beanClass)){
				result.add(beanName);
			}
		}		
		return result;
	}

	public Object getBean(String beanID) {
		BeanDefinition bd = this.getBeanDefinition(beanID);
		if(bd == null){
			return null;
		}
		
		if(bd.isSingleton()){
			Object bean = this.getSingleton(beanID);
			if(bean == null){
				bean = createBean(bd);
				this.registerSingleton(beanID, bean);
			}
			return bean;
		} 
		return createBean(bd);
	}
	protected Object createBean(BeanDefinition bd) {
		//创建实例
		Object bean = instantiateBean(bd);
		//设置属性
		populateBean(bd, bean);
		
		bean = initializeBean(bd,bean);
		
		return bean;		
		
	}

	/**
	 * 创建实例的方法有修改，
	 * @param bd
	 * @return
	 */
	private Object instantiateBean(BeanDefinition bd) {
		// 对于AspectBeforeAdvice，会按照Constructor来创建实例
		if(bd.hasConstructorArgumentValues()){
			ConstructorResolver resolver = new ConstructorResolver(this);
			return resolver.autowireConstructor(bd);
		}else{
			ClassLoader cl = this.getBeanClassLoader();
			String beanClassName = bd.getBeanClassName();
			try {
				Class<?> clz = cl.loadClass(beanClassName);
				return clz.newInstance();
			} catch (Exception e) {			
				throw new BeanCreationException("create bean for "+ beanClassName +" failed",e);
			}	
		}
	}
	protected void populateBean(BeanDefinition bd, Object bean){
		
		for(BeanPostProcessor processor : this.getBeanPostProcessors()){
			if(processor instanceof InstantiationAwareBeanPostProcessor){
				((InstantiationAwareBeanPostProcessor)processor).postProcessPropertyValues(bean, bd.getID());
			}
		}
		
		List<PropertyValue> pvs = bd.getPropertyValues();
		
		if (pvs == null || pvs.isEmpty()) {
			return;
		}
		
		BeanDefinitionValueResolver valueResolver = new BeanDefinitionValueResolver(this);
		SimpleTypeConverter converter = new SimpleTypeConverter(); 
		try{
			for (PropertyValue pv : pvs){
				String propertyName = pv.getName();
				Object originalValue = pv.getValue();
				Object resolvedValue = valueResolver.resolveValueIfNecessary(originalValue);
				
				BeanInfo beanInfo = Introspector.getBeanInfo(bean.getClass());
				PropertyDescriptor[] pds = beanInfo.getPropertyDescriptors();
				for (PropertyDescriptor pd : pds) {
					if(pd.getName().equals(propertyName)){
						Object convertedValue = converter.convertIfNecessary(resolvedValue, pd.getPropertyType());
						pd.getWriteMethod().invoke(bean, convertedValue);
						break;
					}
				}
 
				
			}
		}catch(Exception ex){
			throw new BeanCreationException("Failed to obtain BeanInfo for class [" + bd.getBeanClassName() + "]", ex);
		}	
	}
	protected Object initializeBean(BeanDefinition bd, Object bean)  {
		invokeAwareMethods(bean);	
        //Todo，调用Bean的init方法，暂不实现
		if(!bd.isSynthetic()){
			return applyBeanPostProcessorsAfterInitialization(bean,bd.getID());
		}
		return bean;
	}

	/**
	 * 对于非合成的bean，需要BeanPostProcessor创建代理对象
	 * @param existingBean
	 * @param beanName
	 * @return
	 * @throws BeansException
	 */
	public Object applyBeanPostProcessorsAfterInitialization(Object existingBean, String beanName)
			throws BeansException {

		Object result = existingBean;
		for (BeanPostProcessor beanProcessor : getBeanPostProcessors()) {
			result = beanProcessor.afterInitialization(result, beanName);
			if (result == null) {
				return result;
			}
		}
		return result;
	}

	/**
	 * 根据Bean的生命周期，在这里把BeanFactory注入
	 * @param bean
	 */
	private void invokeAwareMethods(final Object bean) {
		if (bean instanceof BeanFactoryAware) {
			((BeanFactoryAware) bean).setBeanFactory(this);
		}
	}

	public void setBeanClassLoader(ClassLoader beanClassLoader) {
		this.beanClassLoader = beanClassLoader;
	}

    public ClassLoader getBeanClassLoader() {
		return (this.beanClassLoader != null ? this.beanClassLoader : ClassUtils.getDefaultClassLoader());
	}
    public Object resolveDependency(DependencyDescriptor descriptor) {
		
		Class<?> typeToMatch = descriptor.getDependencyType();
		for(BeanDefinition bd: this.beanDefinitionMap.values()){		
			//确保BeanDefinition 有Class对象
			resolveBeanClass(bd);
			Class<?> beanClass = bd.getBeanClass();			
			if(typeToMatch.isAssignableFrom(beanClass)){
				return this.getBean(bd.getID());
			}
		}
		return null;
	}
    public void resolveBeanClass(BeanDefinition bd) {
		if(bd.hasBeanClass()){
			return;
		} else{
			try {
				bd.resolveBeanClass(this.getBeanClassLoader());
			} catch (ClassNotFoundException e) {
				throw new RuntimeException("can't load class:"+bd.getBeanClassName());
			}
		}
	}

    public Class<?> getType(String name) throws NoSuchBeanDefinitionException {
		BeanDefinition bd = this.getBeanDefinition(name);
		if(bd == null){
			throw new NoSuchBeanDefinitionException(name);
		}
		resolveBeanClass(bd);		
		return bd.getBeanClass();
	}
}
