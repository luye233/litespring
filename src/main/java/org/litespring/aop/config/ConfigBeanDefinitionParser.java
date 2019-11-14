package org.litespring.aop.config;



import java.util.ArrayList;
import java.util.List;

import org.litespring.beans.factory.support.BeanDefinitionReaderUtils;
import org.dom4j.Element;
import org.litespring.aop.aspectj.AspectJAfterReturningAdvice;
import org.litespring.aop.aspectj.AspectJAfterThrowingAdvice;
import org.litespring.aop.aspectj.AspectJBeforeAdvice;
import org.litespring.aop.aspectj.AspectJExpressionPointcut;
import org.litespring.beans.BeanDefinition;
import org.litespring.beans.ConstructorArgument;
import org.litespring.beans.PropertyValue;
import org.litespring.beans.factory.config.RuntimeBeanReference;
import org.litespring.beans.factory.support.BeanDefinitionRegistry;
import org.litespring.beans.factory.support.GenericBeanDefinition;
import org.litespring.util.StringUtils;




public class ConfigBeanDefinitionParser /*implements BeanDefinitionParser*/ {

	private static final String ASPECT = "aspect";
	private static final String EXPRESSION = "expression";
	private static final String ID = "id";
	private static final String POINTCUT = "pointcut";
	private static final String POINTCUT_REF = "pointcut-ref";
	private static final String REF = "ref";
	private static final String BEFORE = "before";
	private static final String AFTER = "after";
	private static final String AFTER_RETURNING_ELEMENT = "after-returning";
	private static final String AFTER_THROWING_ELEMENT = "after-throwing";
	private static final String AROUND = "around";
	private static final String ASPECT_NAME_PROPERTY = "aspectName";
	
	public BeanDefinition parse(Element element,BeanDefinitionRegistry registry) {
		
		List<Element> childElts = element.elements();
		for (Element elt: childElts) {
			String localName = elt.getName();
			/*if (POINTCUT.equals(localName)) {
				parsePointcut(elt, registry);
			}*/
			/*else if (ADVISOR.equals(localName)) {
				parseAdvisor(elt, registry);
			}*/
			// 解析这个：<aop:aspect ref="tx">
			if (ASPECT.equals(localName)) {
				parseAspect(elt, registry);
			}
		}
	
		return null;
	}

	

	private void parseAspect(Element aspectElement, BeanDefinitionRegistry registry) {
		String aspectId = aspectElement.attributeValue(ID);
		// 拿到<aop:aspect ref="tx">中ref代表的tx
		String aspectName = aspectElement.attributeValue(REF);

		// 不明白beanDefinitions有什么作用
		List<BeanDefinition> beanDefinitions = new ArrayList<BeanDefinition>();
		List<RuntimeBeanReference> beanReferences = new ArrayList<RuntimeBeanReference>();

		// 这里开始解析aspect下面代表的元素了
		List<Element> eleList = aspectElement.elements();
		boolean adviceFoundAlready = false;
		// 当不是before等拦截器的时候会先跳过
		for (int i=0;i<eleList.size();i++) {
			Element ele = eleList.get(i);
			// 判断是不是before这些（BeforeAdvice等拦截器）
			if (isAdviceNode(ele)) {
				if (!adviceFoundAlready) {
					adviceFoundAlready = true;
					if (!StringUtils.hasText(aspectName)) {
						return;
					}
					// 在第一次解析到Advice的时候要把aspect对象创建出来("tx")
					// 因为没有Advice的情况下，这个aspect就没有什么意义
					beanReferences.add(new RuntimeBeanReference(aspectName));
				}
				// Advice的创建
				GenericBeanDefinition advisorDefinition = parseAdvice(
						aspectName, i, aspectElement, ele, registry, beanDefinitions, beanReferences);
				beanDefinitions.add(advisorDefinition);
			}
		}
		// 先解析Advise，然后再解析Pointcut
		List<Element> pointcuts = aspectElement.elements(POINTCUT);
		for (Element pointcutElement : pointcuts) {
			parsePointcut(pointcutElement, registry);
		}

	}

	
	/**
	 * Return {@code true} if the supplied node describes an advice type. May be one of:
	 * '{@code before}', '{@code after}', '{@code after-returning}',
	 * '{@code after-throwing}' or '{@code around}'.
	 */
	private boolean isAdviceNode(Element ele) {
		
			String name = ele.getName();
			return (BEFORE.equals(name) || AFTER.equals(name) || AFTER_RETURNING_ELEMENT.equals(name) ||
					AFTER_THROWING_ELEMENT.equals(name) || AROUND.equals(name));
		
	}

	// 解析Advise
	// Advice用GenericBeanDefinition来表示，
	private GenericBeanDefinition parseAdvice(
			String aspectName, int order, Element aspectElement, Element adviceElement, BeanDefinitionRegistry registry,
			List<BeanDefinition> beanDefinitions, List<RuntimeBeanReference> beanReferences) {
		// 创建AspectJBeforeAdvice需要Method，AspectJExpressionPointcut, Object来创建
		// Method用GenericBeanDefinition来表示，把targetBeanName和methodName都作为属性，MethodLocatingFactory.class作为beanClass
		GenericBeanDefinition methodDefinition = new GenericBeanDefinition(MethodLocatingFactory.class);
		methodDefinition.getPropertyValues().add(new PropertyValue("targetBeanName", aspectName));
		methodDefinition.getPropertyValues().add(new PropertyValue("methodName", adviceElement.attributeValue("method")));
		methodDefinition.setSynthetic(true);

		// create instance factory definition
		// GenericBeanDefinition来表示AspectInstanceFactory，AspectInstanceFactory的getObject方法来获取Object
		// 把aspectBeanName作为属性，AspectInstanceFactory.class作为beanClass存储在GenericBeanDefinition中
		GenericBeanDefinition aspectFactoryDef =
				new GenericBeanDefinition(AspectInstanceFactory.class);
		aspectFactoryDef.getPropertyValues().add(new PropertyValue("aspectBeanName", aspectName));
		aspectFactoryDef.setSynthetic(true);

		// register the pointcut
		// Pointcut同样也用GenericBeanDefinition来存储
		// 这里也完成了代表整个AspectJBeforeAdvice的GenericBeanDefinition的创建
		GenericBeanDefinition adviceDef = createAdviceDefinition(
				adviceElement, registry, aspectName, order, methodDefinition, aspectFactoryDef,
				beanDefinitions, beanReferences);
		
		adviceDef.setSynthetic(true);


		// register the final advisor
		// 最终把代表整个AspectJBeforeAdvice的GenericBeanDefinition注册到registry中
		// 名称是全称#数字
		BeanDefinitionReaderUtils.registerWithGeneratedName(adviceDef, registry);

		return adviceDef;
		
	}

	/**
	 * Creates the RootBeanDefinition for a POJO advice bean. Also causes pointcut
	 * parsing to occur so that the pointcut may be associate with the advice bean.
	 * This same pointcut is also configured as the pointcut for the enclosing
	 * Advisor definition using the supplied MutablePropertyValues.
	 */
	private GenericBeanDefinition createAdviceDefinition(
			Element adviceElement, BeanDefinitionRegistry registry, String aspectName, int order,
			GenericBeanDefinition methodDef, GenericBeanDefinition aspectFactoryDef,
			List<BeanDefinition> beanDefinitions, List<RuntimeBeanReference> beanReferences) {
		// 创建了beanClass为AspectJBeforeAdvice.class的GenericBeanDefinition，属性中的aspectName是"tx"
		GenericBeanDefinition adviceDefinition = new GenericBeanDefinition(getAdviceClass(adviceElement));
		adviceDefinition.getPropertyValues().add(new PropertyValue(ASPECT_NAME_PROPERTY, aspectName));
		
		// 把AspectJBeforeAdvice中的第一个构造器参数的代表GenericBeanDefinition(MethodLocatingFactory.class)
		// 放到ConstructorArgument中去
		ConstructorArgument cav = adviceDefinition.getConstructorArgument();
		cav.addArgumentValue(methodDef);

		// 处理构造器第二个参数AspectJExpressionPointcut
		Object pointcut = parsePointcutProperty(adviceElement);
		// 如果是BeanDefinition，就在ConstructorArgument和beanDefinitions中加入pointcut
		if (pointcut instanceof BeanDefinition) {
			cav.addArgumentValue(pointcut);
			
			beanDefinitions.add((BeanDefinition) pointcut);
		}
		// 如果是String（说明配置的是个ref，就用RuntimeBeanReference来表示pointcut，
		// 加入到ConstructorArgumen和beanReferences
		else if (pointcut instanceof String) {
			RuntimeBeanReference pointcutRef = new RuntimeBeanReference((String) pointcut);
			cav.addArgumentValue(pointcutRef);		
			beanReferences.add(pointcutRef);
		}
		// 把第三个构造器参数的代表存入ConstructorArgument
		cav.addArgumentValue(aspectFactoryDef);
		
		return adviceDefinition;
	}

	/**
	 * Gets the advice implementation class corresponding to the supplied {@link Element}.
	 */
	// 得到和提供的Element与之相关的实现类，比如AspectJBeforeAdvice等等
	private Class<?> getAdviceClass(Element adviceElement) {
		String elementName = adviceElement.getName();
		if (BEFORE.equals(elementName)) {
			return AspectJBeforeAdvice.class;
		}
		/*else if (AFTER.equals(elementName)) {
			return AspectJAfterAdvice.class;
		}*/
		else if (AFTER_RETURNING_ELEMENT.equals(elementName)) {
			return AspectJAfterReturningAdvice.class;
		}
		else if (AFTER_THROWING_ELEMENT.equals(elementName)) {
			return AspectJAfterThrowingAdvice.class;
		}
		/*else if (AROUND.equals(elementName)) {
			return AspectJAroundAdvice.class;
		}*/
		else {
			throw new IllegalArgumentException("Unknown advice kind [" + elementName + "].");
		}
	}

	/**
	 * Parses the supplied {@code &lt;pointcut&gt;} and registers the resulting
	 * Pointcut with the BeanDefinitionRegistry.
	 */

	/**
	 *  解析pointcut这个Element，然后将pointcut的expression用GenericBeanDefinition封装到registry中
	 *  registry
	 *  	-- beanDefinitionMap
	 *  		-- id：设定，没有就创建，全限定名#0，这里是placeOrder；
	 *  		-- value：GenericBeanDefinition（表达expression）
	 *  			-- GenericBeanDefinition中的propertyValues来存储expression
	 */
	private GenericBeanDefinition parsePointcut(Element pointcutElement,BeanDefinitionRegistry registry) {
		String id = pointcutElement.attributeValue(ID);
		String expression = pointcutElement.attributeValue(EXPRESSION);

		GenericBeanDefinition pointcutDefinition = null;

		//this.parseState.push(new PointcutEntry(id));
		pointcutDefinition = createPointcutDefinition(expression);
		//pointcutDefinition.setSource(parserContext.extractSource(pointcutElement));

		String pointcutBeanName = id;
		if (StringUtils.hasText(pointcutBeanName)) {
			registry.registerBeanDefinition(pointcutBeanName, pointcutDefinition);
		}
		else {
			BeanDefinitionReaderUtils.registerWithGeneratedName(pointcutDefinition, registry);
			
		}
	

		return pointcutDefinition;
	}

	/**
	 * Parses the {@code pointcut} or {@code pointcut-ref} attributes of the supplied
	 * {@link Element} and add a {@code pointcut} property as appropriate. Generates a
	 * {@link org.springframework.beans.factory.config.BeanDefinition} for the pointcut if  necessary
	 * and returns its bean name, otherwise returns the bean name of the referred pointcut.
	 */
	private Object parsePointcutProperty(Element element/*, ParserContext parserContext*/) {
		// 没有pointcut相关配置信息就返回null
		if ((element.attribute(POINTCUT)== null) && (element.attribute(POINTCUT_REF)==null)) {
			/*parserContext.getReaderContext().error(
					"Cannot define both 'pointcut' and 'pointcut-ref' on <advisor> tag.",
					element, this.parseState.snapshot());*/
			return null;
		}
		// 有"point"这个属性就要创建并且返回创建出来的代表pointcut的GenericBeanDefinition
		else if (element.attribute(POINTCUT)!=null) {
			// Create a pointcut for the anonymous pc and register it.
			String expression = element.attributeValue(POINTCUT);
			GenericBeanDefinition pointcutDefinition = createPointcutDefinition(expression);
			//pointcutDefinition.setSource(parserContext.extractSource(element));
			return pointcutDefinition;
		}
		// 在Advice中表示pointcut那个Element中如果有"pointcut-ref"这个的属性的话，就返回这个属性的值
		else if (element.attribute(POINTCUT_REF)!=null) {
			String pointcutRef = element.attributeValue(POINTCUT_REF);
			if (!StringUtils.hasText(pointcutRef)) {
				/*parserContext.getReaderContext().error(
						"'pointcut-ref' attribute contains empty value.", element, this.parseState.snapshot());*/
				return null;
			}
			return pointcutRef;
		}
		else {/*
			parserContext.getReaderContext().error(
					"Must define one of 'pointcut' or 'pointcut-ref' on <advisor> tag.",
					element, this.parseState.snapshot());*/
			return null;
		}
	}

	/**
	 * Creates a {@link BeanDefinition} for the {@link AspectJExpressionPointcut} class using
	 * the supplied pointcut expression.
	 */
	// 创建了PointcutDefinition的数据结构（用GenericBeanDefinition存储）
	protected GenericBeanDefinition createPointcutDefinition(String expression) {
		GenericBeanDefinition beanDefinition = new GenericBeanDefinition(AspectJExpressionPointcut.class);
		beanDefinition.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		beanDefinition.setSynthetic(true);
		beanDefinition.getPropertyValues().add(new PropertyValue(EXPRESSION, expression));
		return beanDefinition;
	}

}
