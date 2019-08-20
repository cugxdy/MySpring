/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.aop.framework;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.aopalliance.aop.Advice;

import org.springframework.aop.Advisor;
import org.springframework.aop.DynamicIntroductionAdvice;
import org.springframework.aop.IntroductionAdvisor;
import org.springframework.aop.IntroductionInfo;
import org.springframework.aop.TargetSource;
import org.springframework.aop.support.DefaultIntroductionAdvisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.target.EmptyTargetSource;
import org.springframework.aop.target.SingletonTargetSource;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;

/**
 * Base class for AOP proxy configuration managers.
 * These are not themselves AOP proxies, but subclasses of this class are
 * normally factories from which AOP proxy instances are obtained directly.
 *
 * <p>This class frees subclasses of the housekeeping of Advices
 * and Advisors, but doesn't actually implement proxy creation
 * methods, which are provided by subclasses.
 *
 * <p>This class is serializable; subclasses need not be.
 * This class is used to hold snapshots of proxies.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @see org.springframework.aop.framework.AopProxy
 */
public class AdvisedSupport extends ProxyConfig implements Advised {

	/** use serialVersionUID from Spring 2.0 for interoperability */
	private static final long serialVersionUID = 2651364800145442165L;


	/**
	 * Canonical TargetSource when there's no target, and behavior is
	 * supplied by the advisors.
	 */
	// 目标类的封装类
	public static final TargetSource EMPTY_TARGET_SOURCE = EmptyTargetSource.INSTANCE;


	/** Package-protected to allow direct access for efficiency */
	TargetSource targetSource = EMPTY_TARGET_SOURCE;

	/** Whether the Advisors are already filtered for the specific target class */
	// 前置过滤器
	private boolean preFiltered = false;

	/** The AdvisorChainFactory to use */
	// AdvisorChainFactory执行器链
	AdvisorChainFactory advisorChainFactory = new DefaultAdvisorChainFactory();

	/** Cache with Method as key and advisor chain List as value */
	// 缓存执行方法对象与通知器链的一对一关系
	private transient Map<MethodCacheKey, List<Object>> methodCache;

	/**
	 * Interfaces to be implemented by the proxy. Held in List to keep the order
	 * of registration, to create JDK proxy with specified order of interfaces.
	 */
	// 代理接口对象
	private List<Class<?>> interfaces = new ArrayList<>();

	/**
	 * List of Advisors. If an Advice is added, it will be wrapped
	 * in an Advisor before being added to this List.
	 */
	// 通知器对象链表
	private List<Advisor> advisors = new LinkedList<>();

	/**
	 * Array updated on changes to the advisors list, which is easier
	 * to manipulate internally.
	 */
	// 通知器对象数组
	private Advisor[] advisorArray = new Advisor[0];


	/**
	 * No-arg constructor for use as a JavaBean.
	 */
	// 创建methodCache为ConcurrentHashMap类型字段
	public AdvisedSupport() {
		this.methodCache = new ConcurrentHashMap<>(32);
	}

	/**
	 * Create a AdvisedSupport instance with the given parameters.
	 * @param interfaces the proxied interfaces
	 */
	public AdvisedSupport(Class<?>... interfaces) {
		this(); // --> 调用无参构造函数
		// 设置interfaces字段
		setInterfaces(interfaces);
	}


	/**
	 * Set the given object as target.
	 * Will create a SingletonTargetSource for the object.
	 * @see #setTargetSource
	 * @see org.springframework.aop.target.SingletonTargetSource
	 */
	public void setTarget(Object target) {
		// 创建并设置SingletonTargetSource类
		setTargetSource(new SingletonTargetSource(target));
	}

	@Override// 设置TargetSource类
	public void setTargetSource(@Nullable TargetSource targetSource) {
		this.targetSource = (targetSource != null ? targetSource : EMPTY_TARGET_SOURCE);
	}

	@Override// 获取TargetSource类
	public TargetSource getTargetSource() {
		return this.targetSource;
	}

	/**
	 * Set a target class to be proxied, indicating that the proxy
	 * should be castable to the given class.
	 * <p>Internally, an {@link org.springframework.aop.target.EmptyTargetSource}
	 * for the given target class will be used. The kind of proxy needed
	 * will be determined on actual creation of the proxy.
	 * <p>This is a replacement for setting a "targetSource" or "target",
	 * for the case where we want a proxy based on a target class
	 * (which can be an interface or a concrete class) without having
	 * a fully capable TargetSource available.
	 * @see #setTargetSource
	 * @see #setTarget
	 */
	public void setTargetClass(Class<?> targetClass) {
		// 创建EmptyTargetSource对象
		this.targetSource = EmptyTargetSource.forClass(targetClass);
	}

	@Override
	public Class<?> getTargetClass() {
		// 获取目标类的Class对象
		return this.targetSource.getTargetClass();
	}

	@Override // 设置前置过滤器
	public void setPreFiltered(boolean preFiltered) {
		this.preFiltered = preFiltered;
	}

	@Override
	public boolean isPreFiltered() {
		return this.preFiltered;
	}

	/**
	 * Set the advisor chain factory to use.
	 * <p>Default is a {@link DefaultAdvisorChainFactory}.
	 */
	// 设置AdvisorChainFactory对象
	public void setAdvisorChainFactory(AdvisorChainFactory advisorChainFactory) {
		Assert.notNull(advisorChainFactory, "AdvisorChainFactory must not be null");
		this.advisorChainFactory = advisorChainFactory;
	}

	/**
	 * Return the advisor chain factory to use (never {@code null}).
	 */
	// 获取AdvisorChainFactory对象
	public AdvisorChainFactory getAdvisorChainFactory() {
		return this.advisorChainFactory;
	}


	/**
	 * Set the interfaces to be proxied.
	 */
	public void setInterfaces(Class<?>... interfaces) {
		// 输入对象不能为空
		Assert.notNull(interfaces, "Interfaces must not be null");
		// 清除
		this.interfaces.clear();
		// 遍历interfaces
		for (Class<?> ifc : interfaces) {
			addInterface(ifc);
		}
	}

	/**
	 * Add a new proxied interface.
	 * @param intf the additional interface to proxy
	 */
	// 将intf添加进interfaces链表中
	public void addInterface(Class<?> intf) {
		// 输入对象不能为空
		Assert.notNull(intf, "Interface must not be null");
		// 输入对象必须为接口对象
		if (!intf.isInterface()) {
			throw new IllegalArgumentException("[" + intf.getName() + "] is not an interface");
		}
		// 防止重复添加intf对象
		if (!this.interfaces.contains(intf)) {
			this.interfaces.add(intf);
			adviceChanged();
		}
	}

	/**
	 * Remove a proxied interface.
	 * <p>Does nothing if the given interface isn't proxied.
	 * @param intf the interface to remove from the proxy
	 * @return {@code true} if the interface was removed; {@code false}
	 * if the interface was not found and hence could not be removed
	 */
	// 删除指定接口
	public boolean removeInterface(Class<?> intf) {
		return this.interfaces.remove(intf);
	}

	@Override
	public Class<?>[] getProxiedInterfaces() {
		// 返回Class类型的数组
		return ClassUtils.toClassArray(this.interfaces);
	}

	@Override
	public boolean isInterfaceProxied(Class<?> intf) {
		for (Class<?> proxyIntf : this.interfaces) {
			// 当intf为proxyIntf的父类或超类时,返回true
			if (intf.isAssignableFrom(proxyIntf)) {
				return true;
			}
		}
		return false;
	}


	@Override// 获取Advisor数组
	public final Advisor[] getAdvisors() {
		return this.advisorArray;
	}

	@Override
	public void addAdvisor(Advisor advisor) {
		int pos = this.advisors.size();
		// 添加至advisors中
		addAdvisor(pos, advisor);
	}

	@Override
	public void addAdvisor(int pos, Advisor advisor) throws AopConfigException {
		// 判断是否为IntroductionAdvisor类型
		if (advisor instanceof IntroductionAdvisor) {
			validateIntroductionAdvisor((IntroductionAdvisor) advisor);
		}
		addAdvisorInternal(pos, advisor);
	}

	@Override // 删除Advisor
	public boolean removeAdvisor(Advisor advisor) {
		int index = indexOf(advisor);
		if (index == -1) {
			return false;
		}
		else {
			// 删除指定位置的Advisor
			removeAdvisor(index);
			return true;
		}
	}

	@Override
	public void removeAdvisor(int index) throws AopConfigException {
		// 判断是否为冻结状态中
		if (isFrozen()) {
			throw new AopConfigException("Cannot remove Advisor: Configuration is frozen.");
		}
		// 判断是否超出索引
		if (index < 0 || index > this.advisors.size() - 1) {
			throw new AopConfigException("Advisor index " + index + " is out of bounds: " +
					"This configuration only has " + this.advisors.size() + " advisors.");
		}

		// 获取Advisor对象
		Advisor advisor = this.advisors.get(index);
		// 当为IntroductionAdvisor对象时
		if (advisor instanceof IntroductionAdvisor) {
			IntroductionAdvisor ia = (IntroductionAdvisor) advisor;
			// We need to remove introduction interfaces.
			for (int j = 0; j < ia.getInterfaces().length; j++) {
				// 移除接口
				removeInterface(ia.getInterfaces()[j]);
			}
		}

		// 删除指定位置的Advisor
		this.advisors.remove(index);
		updateAdvisorArray();
		adviceChanged();
	}

	@Override// 获取advisor在数组中的索引
	public int indexOf(Advisor advisor) {
		Assert.notNull(advisor, "Advisor must not be null");
		return this.advisors.indexOf(advisor);
	}

	@Override // 使用b代替a的位置
	public boolean replaceAdvisor(Advisor a, Advisor b) throws AopConfigException {
		// 检验a与b不能为空
		Assert.notNull(a, "Advisor a must not be null");
		Assert.notNull(b, "Advisor b must not be null");
		// 获取a的索引号
		int index = indexOf(a);
		if (index == -1) {
			return false;
		}
		// 在指定位置上删除a
		removeAdvisor(index);
		// 在指定位置上添加b
		addAdvisor(index, b);
		return true;
	}

	/**
	 * Add all of the given advisors to this proxy configuration.
	 * @param advisors the advisors to register
	 */
	public void addAdvisors(Advisor... advisors) {
		addAdvisors(Arrays.asList(advisors));
	}

	/**
	 * Add all of the given advisors to this proxy configuration.
	 * @param advisors the advisors to register
	 */
	public void addAdvisors(Collection<Advisor> advisors) {
		// 是否处于冻结状态中
		if (isFrozen()) {
			throw new AopConfigException("Cannot add advisor: Configuration is frozen.");
		}
		// advisors是否为空
		if (!CollectionUtils.isEmpty(advisors)) {
			for (Advisor advisor : advisors) {
				// 当为IntroductionAdvisor检验
				if (advisor instanceof IntroductionAdvisor) {
					validateIntroductionAdvisor((IntroductionAdvisor) advisor);
				}
				// 添加advisor至链表中
				Assert.notNull(advisor, "Advisor must not be null");
				this.advisors.add(advisor);
			}
			updateAdvisorArray();
			adviceChanged();
		}
	}

	private void validateIntroductionAdvisor(IntroductionAdvisor advisor) {
		advisor.validateInterfaces();
		// If the advisor passed validation, we can make the change.
		Class<?>[] ifcs = advisor.getInterfaces();
		for (Class<?> ifc : ifcs) {
			addInterface(ifc);
		}
	}

	private void addAdvisorInternal(int pos, Advisor advisor) throws AopConfigException {
		// 不能为空
		Assert.notNull(advisor, "Advisor must not be null");
		// 处于冻结中
		if (isFrozen()) {
			throw new AopConfigException("Cannot add advisor: Configuration is frozen.");
		}
		// IllegalArgumentException异常
		if (pos > this.advisors.size()) {
			throw new IllegalArgumentException(
					"Illegal position " + pos + " in advisor list with size " + this.advisors.size());
		}
		this.advisors.add(pos, advisor);
		// 将advisors转换为数组形式
		updateAdvisorArray();
		// 清理methodCache字段
		adviceChanged();
	}

	/**
	 * Bring the array up to date with the list.
	 */
	protected final void updateAdvisorArray() {
		// 将advisors转换为数组形式
		this.advisorArray = this.advisors.toArray(new Advisor[0]);
	}

	/**
	 * Allows uncontrolled access to the {@link List} of {@link Advisor Advisors}.
	 * <p>Use with care, and remember to {@link #updateAdvisorArray() refresh the advisor array}
	 * and {@link #adviceChanged() fire advice changed events} when making any modifications.
	 */
	// 获取advisors字段
	protected final List<Advisor> getAdvisorsInternal() {
		return this.advisors;
	}


	@Override
	public void addAdvice(Advice advice) throws AopConfigException {
		int pos = this.advisors.size();
		addAdvice(pos, advice);
	}

	/**
	 * Cannot add introductions this way unless the advice implements IntroductionInfo.
	 */
	@Override// 添加advice至advisors中去
	public void addAdvice(int pos, Advice advice) throws AopConfigException {
		Assert.notNull(advice, "Advice must not be null");
		// 当为IntroductionInfo类,抛出异常
		if (advice instanceof IntroductionInfo) {
			// We don't need an IntroductionAdvisor for this kind of introduction:
			// It's fully self-describing.
			addAdvisor(pos, new DefaultIntroductionAdvisor(advice, (IntroductionInfo) advice));
		}
		// 当为DynamicIntroductionAdvice类,抛出异常
		else if (advice instanceof DynamicIntroductionAdvice) {
			// We need an IntroductionAdvisor for this kind of introduction.
			throw new AopConfigException("DynamicIntroductionAdvice may only be added as part of IntroductionAdvisor");
		}
		else {
			// 在指定位置pos上添加DefaultPointcutAdvisor对象
			addAdvisor(pos, new DefaultPointcutAdvisor(advice));
		}
	}

	@Override// 移除指定位置上的Advice
	public boolean removeAdvice(Advice advice) throws AopConfigException {
		int index = indexOf(advice);
		if (index == -1) {
			return false;
		}
		else {
			removeAdvisor(index);
			return true;
		}
	}

	@Override// 获取advice所在的索引号
	public int indexOf(Advice advice) {
		Assert.notNull(advice, "Advice must not be null");
		// 遍历advisors数组
		for (int i = 0; i < this.advisors.size(); i++) {
			Advisor advisor = this.advisors.get(i);
			// 获取Advice对象
			if (advisor.getAdvice() == advice) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Is the given advice included in any advisor within this proxy configuration?
	 * @param advice the advice to check inclusion of
	 * @return whether this advice instance is included
	 */
	// 是否已经包含advice对象
	public boolean adviceIncluded(@Nullable Advice advice) {
		if (advice != null) {
			for (Advisor advisor : this.advisors) {
				// 获取Advice对象
				if (advisor.getAdvice() == advice) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Count advices of the given class.
	 * @param adviceClass the advice class to check
	 * @return the count of the interceptors of this class or subclasses
	 */
	public int countAdvicesOfType(@Nullable Class<?> adviceClass) {
		int count = 0;
		if (adviceClass != null) {
			for (Advisor advisor : this.advisors) {
				//这个对象能不能被转化为这个类
				//1.一个对象是本身类的一个对象
				//2.一个对象能被转化为本身类所继承类(父类的父类等)和实现的接口(接口的父接口)强转
				//3.所有对象都能被Object的强转
				//4.凡是null有关的都是false  class.inInstance(null)
				if (adviceClass.isInstance(advisor.getAdvice())) {
					count++;
				}
			}
		}
		return count;
	}


	/**
	 * Determine a list of {@link org.aopalliance.intercept.MethodInterceptor} objects
	 * for the given method, based on this configuration.
	 * @param method the proxied method
	 * @param targetClass the target class
	 * @return List of MethodInterceptors (may also include InterceptorAndDynamicMethodMatchers)
	 */
	public List<Object> getInterceptorsAndDynamicInterceptionAdvice(Method method, @Nullable Class<?> targetClass) {
		// 创建MethodCacheKey对象
		MethodCacheKey cacheKey = new MethodCacheKey(method);
		List<Object> cached = this.methodCache.get(cacheKey);
		if (cached == null) {
			// 获取缓存对象
			cached = this.advisorChainFactory.getInterceptorsAndDynamicInterceptionAdvice(
					this, method, targetClass);
			// 缓存
			this.methodCache.put(cacheKey, cached);
		}
		return cached;
	}

	/**
	 * Invoked when advice has changed.
	 */
	protected void adviceChanged() {
		// 清理methodCache字段
		this.methodCache.clear();
	}

	/**
	 * Call this method on a new instance created by the no-arg constructor
	 * to create an independent copy of the configuration from the given object.
	 * @param other the AdvisedSupport object to copy configuration from
	 */
	protected void copyConfigurationFrom(AdvisedSupport other) {
		copyConfigurationFrom(other, other.targetSource, new ArrayList<>(other.advisors));
	}

	/**
	 * Copy the AOP configuration from the given AdvisedSupport object,
	 * but allow substitution of a fresh TargetSource and a given interceptor chain.
	 * @param other the AdvisedSupport object to take proxy configuration from
	 * @param targetSource the new TargetSource
	 * @param advisors the Advisors for the chain
	 */
	protected void copyConfigurationFrom(AdvisedSupport other, TargetSource targetSource, List<Advisor> advisors) {
		copyFrom(other);
		this.targetSource = targetSource;
		// 设置advisorChainFactory字段
		this.advisorChainFactory = other.advisorChainFactory;
		this.interfaces = new ArrayList<>(other.interfaces);
		for (Advisor advisor : advisors) {
			if (advisor instanceof IntroductionAdvisor) {
				// 当为IntroductionAdvisor时,检验advisor对象
				validateIntroductionAdvisor((IntroductionAdvisor) advisor);
			}
			Assert.notNull(advisor, "Advisor must not be null");
			this.advisors.add(advisor);
		}
		updateAdvisorArray();
		adviceChanged();
	}

	/**
	 * Build a configuration-only copy of this AdvisedSupport,
	 * replacing the TargetSource
	 */
	AdvisedSupport getConfigurationOnlyCopy() {
		AdvisedSupport copy = new AdvisedSupport();
		copy.copyFrom(this);
		copy.targetSource = EmptyTargetSource.forClass(getTargetClass(), getTargetSource().isStatic());
		copy.advisorChainFactory = this.advisorChainFactory;
		copy.interfaces = this.interfaces;
		copy.advisors = this.advisors;
		copy.updateAdvisorArray();
		return copy;
	}


	//---------------------------------------------------------------------
	// Serialization support
	//---------------------------------------------------------------------

	private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
		// Rely on default serialization; just initialize state after deserialization.
		ois.defaultReadObject();

		// Initialize transient fields.
		this.methodCache = new ConcurrentHashMap<>(32);
	}


	@Override
	public String toProxyConfigString() {
		return toString();
	}

	/**
	 * For debugging/diagnostic use.
	 */
	@Override // 返回String对象
	public String toString() {
		StringBuilder sb = new StringBuilder(getClass().getName());
		sb.append(": ").append(this.interfaces.size()).append(" interfaces ");
		sb.append(ClassUtils.classNamesToString(this.interfaces)).append("; ");
		sb.append(this.advisors.size()).append(" advisors ");
		sb.append(this.advisors).append("; ");
		sb.append("targetSource [").append(this.targetSource).append("]; ");
		sb.append(super.toString());
		return sb.toString();
	}


	/**
	 * Simple wrapper class around a Method. Used as the key when
	 * caching methods, for efficient equals and hashCode comparisons.
	 */
	private static final class MethodCacheKey implements Comparable<MethodCacheKey> {

		// 缓存方法对象
		private final Method method;

		// 哈希码
		private final int hashCode;

		// 创建MethodCacheKey对象
		public MethodCacheKey(Method method) {
			this.method = method;
			this.hashCode = method.hashCode();
		}

		@Override// 判断两个对象是否为空
		public boolean equals(Object other) {
			return (this == other || (other instanceof MethodCacheKey &&
					this.method == ((MethodCacheKey) other).method));
		}

		@Override // 获取哈希码
		public int hashCode() {
			return this.hashCode;
		}

		@Override// 返回String对象
		public String toString() {
			return this.method.toString();
		}

		@Override // 比较两个MethodCacheKey对象
		public int compareTo(MethodCacheKey other) {
			int result = this.method.getName().compareTo(other.method.getName());
			if (result == 0) {
				result = this.method.toString().compareTo(other.method.toString());
			}
			return result;
		}
	}

}
