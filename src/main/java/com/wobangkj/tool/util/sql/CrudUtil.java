package com.wobangkj.tool.util.sql;

import com.wobangkj.tool.api.GetInfo;
import com.wobangkj.tool.api.GetInfoN;
import com.wobangkj.tool.api.MapData;
import com.wobangkj.tool.lib.Lib;
import com.wobangkj.tool.manager.cache.CacheManager;
import com.wobangkj.tool.manager.cache.impl.RedisManager;
import com.wobangkj.tool.model.CacheModel;
import com.wobangkj.tool.model.KeyModel;
import com.wobangkj.tool.util.Util;
import com.wobangkj.tool.lib.Constants;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import javax.persistence.EntityNotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * des: 通用增删改查
 * des: 缓存待完善...
 *
 * @author dreamlu
 */

public class CrudUtil {

	/**
	 * 缓存
	 * 方式一： mysql表全部数据-->redis表全部数据-->查询
	 * 方式二： 根据不同查询条件存储redis,设置失效时间, 命中延长时间
	 * 此处方式二： 通用增删改查方式, 建议根据具体业务逻辑实现对应缓存
	 * 缓存操作参考： {@link CacheManager}
	 */
	private static CacheManager cacheManager;

	public static void setCacheManager(RedisConnectionFactory redisConnectionFactory) {
		CrudUtil.cacheManager = new RedisManager(redisConnectionFactory);
	}

	/**
	 * des: JpaRepository repository 属性不抽取, 防止并发引用, 全局问题
	 */
	// search
	// params include data
	@SuppressWarnings("Duplicates")
	public static Object search(Map<String, Object> params, Object data, JpaRepository repository) {

		// 缓存
		// key
		String key = new KeyModel(repository, params).toString();
		Object res;
		if (cacheManager != null) {
			// data
			CacheModel cacheModel = cacheManager.get(key);
			// 数据存在
			if (cacheModel != null) {
				// data
				res = cacheModel.getData();
				// 延长时间
				cacheManager.check(key);

				// return data
				return res;
			}

		}

		// 无缓存
		try {
			// 创建 ExampleMatcher
			// 过滤查询条件, 但不会拦截返回结果
			ExampleMatcher exampleMatcher = ExampleMatcher.matching()
					// 忽略 id 和 createTime 字段。
					//.withIgnorePaths("id")
					// 忽略大小写。
					.withIgnoreCase()
					// 忽略为空字段。
					.withIgnoreNullValues();

			// 携带 ExampleMatcher。
			Example<Object> example = Example.of(data, exampleMatcher);

			// 分页查询，从 0 页开始查询 everyPage 个。
			int  clientPage, everyPage;
			Page page;

			// every参数判断
			// 返回所有, 不分页
			String every = (String) params.get("every");
			// 查询所有数据
			if ("all".equals(every)) {

				// 查询数据
				List content = repository.findAll(example, new Sort(Sort.Direction.DESC, "id"));
				if(content.size() == 0) {
					return Lib.MapNoResult;
				}
				res = Lib.GetMapData(Lib.CodeSuccess, Lib.MsgSuccess, content);
				// 缓存开启判断
				if (cacheManager != null) {

					// 10分钟过期
					cacheManager.set(key, new CacheModel(Constants.CACHE_MINUTE, res));
				}

				//List content = repository.findAll(example, new Sort(Sort.Direction.DESC, "id"));
				return res;
			} else {

				// 分页查询，从 0 页开始查询 everyPage 个。
				clientPage = Integer.parseInt((String) params.get("clientPage"));
				everyPage = Integer.parseInt((String) params.get("everyPage"));

				// data
				page = repository.findAll(example, PageRequest.of(clientPage - 1, everyPage, new Sort(Sort.Direction.DESC, "id")));

			}

			// 分页表
			List content = page.getContent();
			if (content.size() == 0) {
				return Lib.GetMapData(Lib.CodeNoResult, Lib.MsgNoResult);
			}

			// ignore参数判断v1
			// 反射原理
			// 返回数据拦截处理(将值赋值为null)
			/*String ignore = (String) params.get("ignore");
			if (ignore != null) {
				String[] ignores = ignore.split(",");
				for (Object data : content) {
					for (String field : ignores) {
						Util.setFieldValue(field, null, data);
					}
				}
			}*/

			// ignore参数判断v2
			// 第三方json包
			// 返回数据拦截处理(将字段直接删除)
			// 小bug, 将json中null字段同时删除
			// JSONObject jsonObject=new JSONObject(content);
			// JSONArray jsonArray= jsonObject.getJSONArray(null);
			String ignore = (String) params.get("ignore");
			if (ignore != null && ignore.length() != 0 && content.size() != 0) {
				String[]  ignores   = ignore.split(",");
				JSONArray jsonArray = new JSONArray(content);
				// Iterator<String> iterator = ignores
				for (String field : ignores) {
					for (int i = 0; i < jsonArray.length(); i++) {
						JSONObject jsonData = jsonArray.getJSONObject(i);//得到对象中的第i条记录
						jsonData.remove(field);
					}
				}
				content = jsonArray.toList();
			}


			// 总数量 替换 总页数
			// 针对一些接口需要统计总数量问题, 不必重写接口
			long sumPage = page.getTotalElements();//.getTotalPages();

			res = Lib.GetMapDataPager(content, clientPage, (int) sumPage, everyPage);
			// 缓存开启判断
			if (cacheManager != null) {

				// 10分钟过期
				cacheManager.set(key, new CacheModel(Constants.CACHE_MINUTE, res));
			}
			return res;

		} catch (IllegalArgumentException ex) {
			return Lib.GetMapData(Lib.CodeText, Lib.MsgArgsErr);
		} catch (EmptyResultDataAccessException ex) {
			return Lib.GetMapData(Lib.CodeText, Lib.MsgNoData);
		} catch (Exception e) {
			// 暂无数据
			if (e.getCause().getClass() == EntityNotFoundException.class) {
				return Lib.MapNoResult;
			}

			throw e;
//			return Lib.GetMapData(Lib.CodeSql, e.getCause().getCause().getMessage());
		}
	}

	// get by id
	// create
	public static Object getById(Object id, JpaRepository repository) {

		// 缓存
		// key
		String   key = new KeyModel(repository, id).toString();
		GetInfoN res;
		if (cacheManager != null) {
			// data
			CacheModel cacheModel = cacheManager.get(key);
			// 数据存在
			if (cacheModel != null) {
				// data
				res = (GetInfo) cacheModel.getData();
				// 延长时间
				cacheManager.check(key);

				// return data
				return res;
			}

		}

		try {
			Object data = repository.findById(id).get();

			res = Lib.GetMapData(Lib.CodeSuccess, Lib.MsgSuccess, data);

			if(cacheManager != null) {
				cacheManager.set(key, new CacheModel(Constants.CACHE_MINUTE, res));
			}
			return res;
		} catch (NoSuchElementException ex) {
			return Lib.GetMapData(Lib.CodeText, Lib.MsgNoData);
		} catch (Exception e) {
			return Lib.GetMapData(Lib.CodeSql, e.getCause().getCause().getMessage());
		}
	}

	// delete
	public static MapData delete(Object id, JpaRepository repository) {

		// 缓存开启判断
		// 简单点,删除缓存
		if (cacheManager != null) {
			cacheManager.deletePrex("*" + repository.toString() + "*");
		}

		try {
			repository.deleteById(id);
			return Lib.MapDelete;
		} catch (EmptyResultDataAccessException ex) {
			return Lib.GetMapData(Lib.CodeText, Lib.MsgNoData);
		} catch (Exception e) {
			return Lib.GetMapData(Lib.CodeSql, e.getCause().getCause().getMessage());
		}
	}

	// update
	public static MapData update(Object data, JpaRepository repository) {

		// 缓存开启判断
		// 简单点,删除缓存
		if (cacheManager != null) {
			cacheManager.deletePrex("*" + repository.toString() + "*");
		}

		try {
			Object udateData = repository.save(data);
			Object id        = Util.getFieldValue("id", udateData);
			if (id == null) {
				return Lib.GetMapData(Lib.CodeText, "id不能为空");
			}
			return Lib.MapUpdate;
		} catch (NoSuchElementException e) {
			return Lib.GetMapData(Lib.CodeSql, "条件值不存在");
		} catch (Exception e) {
			return Lib.GetMapData(Lib.CodeSql, e.getCause().getCause().getMessage());
		}
	}

	// create
	// return id
	public static GetInfoN create(Object data, JpaRepository repository) {

		// 缓存开启判断
		// 简单点,删除缓存
		if (cacheManager != null) {
			cacheManager.deletePrex("*" + repository.toString() + "*");
		}

		if(Util.isObjEmpty(data)) {
			return Lib.GetMapData(Lib.CodeValidateErr, "数据不能全部为空", null);
		}

		try {
			Object createData = repository.save(data);
			return Lib.GetMapData(Lib.CodeCreate, Lib.MsgCreate, new HashMap<String, Object>() {
				{
					put("id", Util.getFieldValue("id", createData));
				}
			});
		} catch (Exception e) {
			return Lib.GetMapData(Lib.CodeSql, e.getCause().getCause().getMessage(), null);
		}
	}
}
