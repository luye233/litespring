package org.litespring.beans.factory;


public interface FactoryBean<T> {

	// 核心就是这个getObject方法，定义了获取bean的方式
	T getObject() throws Exception;

	Class<?> getObjectType();

}
