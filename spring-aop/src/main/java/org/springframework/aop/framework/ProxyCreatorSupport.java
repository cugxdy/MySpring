/*
 * Copyright 2002-2016 the original author or authors.
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

import java.util.LinkedList;
import java.util.List;

import org.springframework.util.Assert;

/**
 * Base class for proxy factories.
 * Provides convenient access to a configurable AopProxyFactory.
 *
 * @author Juergen Hoeller
 * @since 2.0.3
 * @see #createAopProxy()
 */
@SuppressWarnings("serial")
public class ProxyCreatorSupport extends AdvisedSupport {

	// Aop代理工厂
	private AopProxyFactory aopProxyFactory;

	// 监听器
	private List<AdvisedSupportListener> listeners = new LinkedList<>();

	/** Set to true when the first AOP proxy has been created */
	// 当AOP代理对象创建时,active = true;
	private boolean active = false;


	/**
	 * Create a new ProxyCreatorSupport instance.
	 */
	public ProxyCreatorSupport() {
		// 创建DefaultAopProxyFactory类
		this.aopProxyFactory = new DefaultAopProxyFactory();
	}

	/**
	 * Create a new ProxyCreatorSupport instance.
	 * @param aopProxyFactory the AopProxyFactory to use
	 */
	public ProxyCreatorSupport(AopProxyFactory aopProxyFactory) {
		// 空指针异常
		Assert.notNull(aopProxyFactory, "AopProxyFactory must not be null");
		this.aopProxyFactory = aopProxyFactory;
	}


	/**
	 * Customize the AopProxyFactory, allowing different strategies
	 * to be dropped in without changing the core framework.
	 * <p>Default is {@link DefaultAopProxyFactory}, using dynamic JDK
	 * proxies or CGLIB proxies based on the requirements.
	 */
	public void setAopProxyFactory(AopProxyFactory aopProxyFactory) {
		// 设置AopProxyFactory字段
		Assert.notNull(aopProxyFactory, "AopProxyFactory must not be null");
		this.aopProxyFactory = aopProxyFactory;
	}

	/**
	 * Return the AopProxyFactory that this ProxyConfig uses.
	 */
	// 获取AopProxyFactory类
	public AopProxyFactory getAopProxyFactory() {
		return this.aopProxyFactory;
	}

	/**
	 * Add the given AdvisedSupportListener to this proxy configuration.
	 * @param listener the listener to register
	 */
	// 添加指定AdvisedSupportListener
	public void addListener(AdvisedSupportListener listener) {
		Assert.notNull(listener, "AdvisedSupportListener must not be null");
		this.listeners.add(listener);
	}

	/**
	 * Remove the given AdvisedSupportListener from this proxy configuration.
	 * @param listener the listener to deregister
	 */
	// 删除指定AdvisedSupportListener
	public void removeListener(AdvisedSupportListener listener) {
		Assert.notNull(listener, "AdvisedSupportListener must not be null");
		this.listeners.remove(listener);
	}


	/**
	 * Subclasses should call this to get a new AOP proxy. They should <b>not</b>
	 * create an AOP proxy with {@code this} as an argument.
	 */
	// 同步代码块,创建AopProxy代理类
	protected final synchronized AopProxy createAopProxy() {
		if (!this.active) {
			activate();
		}
		// 获取aopProxyFactory创建代理对象
		return getAopProxyFactory().createAopProxy(this);
	}

	/**
	 * Activate this proxy configuration.
	 * @see AdvisedSupportListener#activated
	 */
	private void activate() {
		// 设置active为true
		this.active = true;
		for (AdvisedSupportListener listener : this.listeners) {
			// 触发监听器
			listener.activated(this);
		}
	}

	/**
	 * Propagate advice change event to all AdvisedSupportListeners.
	 * @see AdvisedSupportListener#adviceChanged
	 */
	@Override// 当advice更新时,触发adviceChanged方法
	protected void adviceChanged() {
		super.adviceChanged();
		synchronized (this) {
			if (this.active) {
				// 遍历listeners链表
				for (AdvisedSupportListener listener : this.listeners) {
					// 执行adviceChanged方法
					listener.adviceChanged(this);
				}
			}
		}
	}

	/**
	 * Subclasses can call this to check whether any AOP proxies have been created yet.
	 */
	// 同步代码块,获取active字段
	protected final synchronized boolean isActive() {
		return this.active;
	}

}
