package com.wobangkj.tool.manager.token.impl;

import com.wobangkj.tool.manager.cache.CacheManager;
import com.wobangkj.tool.manager.token.TokenManager;
import com.wobangkj.tool.model.CacheModel;
import com.wobangkj.tool.model.TokenModel;

import java.util.UUID;

/**
 * com.wobangkj.apigateway.manager.impl
 */
public class RedisTokenManager implements TokenManager {

	private CacheManager cacheManager;

	public RedisTokenManager(CacheManager cacheManager) {
		this.cacheManager = cacheManager;
	}

	public TokenModel createToken(long userId, CacheModel cacheModel) {
		// 使用 uuid 作为源 token
		String     token = UUID.randomUUID().toString().replace("-", "");
		TokenModel model = new TokenModel(userId, token);
		// 30 分钟有效期
		cacheManager.set(model.getToken(), cacheModel);
		return model;
	}

	public TokenModel getToken(String authentication) {
		if (authentication == null || authentication.length() == 0) {
			return null;
		}
		String[] param = authentication.split("_");
		if (param.length != 2) {
			return null;
		}
		// 使用 userId 和源 token 简单拼接成的 token，可以增加加密措施
		long   userId = Long.parseLong(param[0]);
		String token  = param[1];
		return new TokenModel(userId, token);
	}

	public boolean checkToken(TokenModel model) {
		return false;
	}

	public void deleteToken(long userId) {

	}
}
