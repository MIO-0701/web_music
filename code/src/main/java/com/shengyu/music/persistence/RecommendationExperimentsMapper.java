package com.shengyu.music.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 持久层 Mapper：连接 MySQL shengyu 库的 recommendation_experiments：离线算法的精确率、召回率和覆盖率结果。 提供该表的数据访问方法。 */
@Mapper
public interface RecommendationExperimentsMapper extends BaseMapper<RecommendationExperiments> {}
