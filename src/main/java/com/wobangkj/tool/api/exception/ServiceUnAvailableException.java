package com.wobangkj.tool.api.exception;

/**
 * @Description: 服务不可用异常
 */
public class ServiceUnAvailableException extends RuntimeException {

	private String serviceId;

	public ServiceUnAvailableException(String serviceId) {
		this.serviceId = serviceId;
	}

	public String getServiceId() {
		return serviceId;
	}
}

