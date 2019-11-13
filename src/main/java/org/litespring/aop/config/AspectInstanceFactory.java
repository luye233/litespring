package org.litespring.aop.config;



import org.litespring.beans.factory.BeanFactory;
import org.litespring.beans.factory.BeanFactoryAware;
import org.litespring.util.StringUtils;


/**
 * Implementation of {@link AspectInstanceFactory} that locates the aspect from the
 * {@link org.springframework.beans.factory.BeanFactory} using a configured bean name.
 *	这里的注释都讲解好了，就是借助一个bean name去获取BeanFactory中的aspect（方法）
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @since 2.0
 */
public class AspectInstanceFactory implements BeanFactoryAware{

	private String aspectBeanName;

	private BeanFactory beanFactory;

	public void setAspectBeanName(String aspectBeanName) {
		this.aspectBeanName = aspectBeanName;
	}

	public void setBeanFactory(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
		if (!StringUtils.hasText(this.aspectBeanName)) {
			throw new IllegalArgumentException("'aspectBeanName' is required");
		}
	}

	// 获取方法
	public Object getAspectInstance() throws Exception {
		return this.beanFactory.getBean(this.aspectBeanName);
	}
}
