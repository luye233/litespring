package org.litespring.beans.factory.support;

import org.litespring.beans.BeanDefinition;
import org.litespring.beans.factory.BeanCreationException;
import org.litespring.beans.factory.config.ConfigurableBeanFactory;

/**
 * 抽象出一个抽象类来继承实现相关父类，接口，让DefaultBeanFactory获得一定的解耦
 */
public abstract class AbstractBeanFactory extends DefaultSingletonBeanRegistry implements ConfigurableBeanFactory {
	// 这里用来protected，只允许子类使用，没有将createBean方法暴露给客户端
	protected abstract Object createBean(BeanDefinition bd) throws BeanCreationException;
}
